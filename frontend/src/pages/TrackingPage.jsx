import { useEffect, useState, useRef, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  MapContainer, TileLayer, Marker, Popup, useMap, Circle,
} from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { groupApi, locationApi } from '../services/api';
import { connectSocket, disconnectSocket } from '../services/socket';
import { useAuth } from '../context/AuthContext';
import { useLocationSharing } from '../hooks/useLocationSharing';

/* ─── Leaflet default-icon fix for Vite bundler ─── */
delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon-2x.png',
  iconUrl:       'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon.png',
  shadowUrl:     'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
});

/* ─── Palette: a distinct color per member (cycles) ─── */
const PALETTE = [
  '#2563EB', '#10B981', '#F59E0B', '#EF4444',
  '#8B5CF6', '#06B6D4', '#EC4899', '#14B8A6',
];

/** Build a circular SVG marker DivIcon for a given color & initials */
function makeCustomIcon(color, initials, isSelf) {
  const size = isSelf ? 42 : 36;
  const ring = isSelf ? `stroke="${color}" stroke-width="3"` : '';
  const svg = `
    <svg xmlns="http://www.w3.org/2000/svg" width="${size + 8}" height="${size + 8}" viewBox="0 0 ${size + 8} ${size + 8}">
      <circle cx="${(size + 8) / 2}" cy="${(size + 8) / 2}" r="${size / 2 + 1}" fill="${color}22" ${ring}/>
      <circle cx="${(size + 8) / 2}" cy="${(size + 8) / 2}" r="${size / 2 - 1}" fill="${color}"/>
      <text x="${(size + 8) / 2}" y="${(size + 8) / 2 + 5}" text-anchor="middle"
            font-size="${isSelf ? 14 : 12}" font-weight="700" fill="white" font-family="system-ui,sans-serif">
        ${initials}
      </text>
    </svg>`;
  return L.divIcon({
    html: svg,
    className: '',
    iconSize:   [size + 8, size + 8],
    iconAnchor: [(size + 8) / 2, (size + 8) / 2],
    popupAnchor: [0, -(size / 2 + 4)],
  });
}

/* ─── Subcomponent: recenter & fit-bounds ─── */
function MapController({ members, focusTarget }) {
  const map = useMap();

  // Fit all online markers on initial load or when members change
  useEffect(() => {
    const online = Object.values(members).filter(m => m.lat !== 0 && m.lng !== 0);
    if (online.length === 0) return;
    const bounds = L.latLngBounds(online.map(m => [m.lat, m.lng]));
    map.fitBounds(bounds.pad(0.25), { maxZoom: 16, animate: true });
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // Pan to a focused member
  useEffect(() => {
    if (focusTarget && focusTarget.lat !== 0) {
      map.flyTo([focusTarget.lat, focusTarget.lng], 16, { duration: 1.2 });
    }
  }, [focusTarget, map]);

  return null;
}

/* ─── Relative time helper ─── */
function relTime(ts) {
  if (!ts) return '';
  const secs = Math.floor((Date.now() - ts) / 1000);
  if (secs < 10) return 'just now';
  if (secs < 60) return `${secs}s ago`;
  const mins = Math.floor(secs / 60);
  if (mins < 60) return `${mins}m ago`;
  return `${Math.floor(mins / 60)}h ago`;
}

/* ════════════════════════════════════════════
   Main Page
   ════════════════════════════════════════════ */
export default function TrackingPage() {
  const { groupId } = useParams();
  const { user }    = useAuth();
  const navigate    = useNavigate();

  const [group,       setGroup]       = useState(null);
  const [members,     setMembers]     = useState({});   // userId → LocationResponse
  const [alerts,      setAlerts]      = useState([]);
  const [focusTarget, setFocusTarget] = useState(null);
  const [panelOpen,   setPanelOpen]   = useState(true); // mobile toggle
  const [lastPoll,    setLastPoll]    = useState(null);
  const [colorMap,    setColorMap]    = useState({});   // userId → color

  const alertTimers = useRef([]);

  const { sharing, accuracy, gpsError, startSharing, stopSharing } =
    useLocationSharing(groupId);

  /* ── Assign stable colors to members ── */
  const assignColor = useCallback((uid) => {
    setColorMap(prev => {
      if (prev[uid]) return prev;
      const idx = Object.keys(prev).length % PALETTE.length;
      return { ...prev, [uid]: PALETTE[idx] };
    });
  }, []);

  /* ── Polling + initial load ── */
  useEffect(() => {
    const fetchAll = () => {
      groupApi.get(groupId).then(r => setGroup(r.data)).catch(() => {});
      locationApi.getGroup(groupId).then(r => {
        const map = {};
        r.data.forEach(loc => {
          map[loc.userId] = loc;
          assignColor(loc.userId);
        });
        setMembers(map);
        setLastPoll(Date.now());
      }).catch(() => {});
    };

    fetchAll();
    const interval = setInterval(fetchAll, 10_000);
    return () => clearInterval(interval);
  }, [groupId, assignColor]);

  /* ── WebSocket live updates ── */
  useEffect(() => {
    connectSocket(
      groupId,
      (loc) => {
        assignColor(loc.userId);
        setMembers(prev => ({ ...prev, [loc.userId]: loc }));
      },
      (alertMsg) => {
        const id = Date.now();
        setAlerts(prev => [{ id, msg: alertMsg }, ...prev.slice(0, 4)]);
        const t = setTimeout(
          () => setAlerts(prev => prev.filter(a => a.id !== id)),
          6000,
        );
        alertTimers.current.push(t);
      },
    );
    return () => {
      disconnectSocket();
      alertTimers.current.forEach(clearTimeout);
    };
  }, [groupId, assignColor]);

  /* ── Derived data ── */
  const memberList  = Object.values(members);
  const onlineCount = memberList.filter(m => m.status === 'ONLINE').length;

  /* ── Locate-me: pan to own position ── */
  const locateMe = () => {
    if (user && members[user.userId]) {
      setFocusTarget(members[user.userId]);
    }
  };

  return (
    <div style={styles.page}>
      {/* ── Toast Alerts ── */}
      {alerts.length > 0 && (
        <div className="toast-container">
          {alerts.map(a => (
            <div key={a.id} className="toast">{a.msg}</div>
          ))}
        </div>
      )}

      {/* ── Header ── */}
      <header style={styles.header}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <button
            id="back-to-dashboard-btn"
            className="btn btn-ghost"
            style={{ padding: '6px 14px', fontSize: '0.85rem' }}
            onClick={() => navigate('/dashboard')}
          >
            ← Back
          </button>
          <div>
            <h1 style={styles.groupName}>{group?.name || 'Loading…'}</h1>
            <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginTop: 2 }}>
              <span className="live-badge">Live</span>
              {group?.inviteCode && (
                <span style={styles.code}>{group.inviteCode}</span>
              )}
              <span style={styles.onlinePill}>
                <span style={styles.onlineDot} />
                {onlineCount} online
              </span>
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          {/* Last-updated timestamp */}
          {lastPoll && (
            <span style={styles.pollLabel}>Polled {relTime(lastPoll)}</span>
          )}

          {/* Locate-me */}
          <button
            id="locate-me-btn"
            className="btn btn-ghost"
            style={{ padding: '8px 14px', fontSize: '0.85rem' }}
            onClick={locateMe}
            title="Centre on my location"
          >
            🎯
          </button>

          {/* Share toggle */}
          <button
            id={sharing ? 'stop-sharing-btn' : 'start-sharing-btn'}
            className={`btn ${sharing ? 'btn-danger' : 'btn-secondary'}`}
            onClick={sharing ? stopSharing : startSharing}
            style={{ padding: '10px 20px' }}
          >
            {sharing ? '⏹ Stop' : '📡 Share Location'}
          </button>

          {/* Mobile panel toggle */}
          <button
            id="toggle-panel-btn"
            className="btn btn-ghost"
            style={{ padding: '8px 12px', display: 'none' }}
            onClick={() => setPanelOpen(v => !v)}
            title="Toggle members panel"
          >
            👥
          </button>
        </div>
      </header>

      {/* ── Accuracy / GPS error banner ── */}
      {(sharing || gpsError) && (
        <div style={{
          ...styles.gpsBar,
          background: gpsError ? 'rgba(239,68,68,0.12)' : 'rgba(16,185,129,0.08)',
          borderBottom: `1px solid ${gpsError ? 'rgba(239,68,68,0.25)' : 'rgba(16,185,129,0.2)'}`,
        }}>
          {gpsError ? (
            <span style={{ color: 'var(--danger)' }}>⚠️ GPS error: {gpsError}</span>
          ) : (
            <span style={{ color: 'var(--secondary)' }}>
              📡 Sharing — accuracy ±{accuracy ?? '…'}m
            </span>
          )}
        </div>
      )}

      {/* ── Main layout ── */}
      <div style={styles.body}>
        {/* Map */}
        <div style={styles.mapWrapper}>
          <MapContainer
            center={[28.6139, 77.2090]}
            zoom={13}
            style={{ width: '100%', height: '100%' }}
            zoomControl={false}
          >
            <TileLayer
              attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
              url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
              className="map-tiles"
            />

            <MapController members={members} focusTarget={focusTarget} />

            {memberList.map(loc => {
              if (loc.lat === 0 && loc.lng === 0) return null;
              const isSelf  = loc.userId === user?.userId;
              const color   = colorMap[loc.userId] || PALETTE[0];
              const initials = (loc.userName || '?').slice(0, 2).toUpperCase();
              const icon = makeCustomIcon(color, initials, isSelf);

              return (
                <Marker
                  key={loc.userId}
                  position={[loc.lat, loc.lng]}
                  icon={icon}
                >
                  {/* Accuracy circle for self */}
                  {isSelf && accuracy && (
                    <Circle
                      center={[loc.lat, loc.lng]}
                      radius={accuracy}
                      pathOptions={{ color, fillColor: color, fillOpacity: 0.08, weight: 1 }}
                    />
                  )}
                  <Popup>
                    <div style={styles.popup}>
                      <div style={{ ...styles.popupDot, background: color }} />
                      <div>
                        <strong style={{ display: 'block', fontSize: '0.9rem' }}>
                          {loc.userName}{isSelf ? ' (you)' : ''}
                        </strong>
                        <span style={{ fontSize: '0.75rem', color: '#666' }}>
                          {loc.status} · {relTime(loc.timestamp)}
                        </span>
                        <br />
                        <span style={{ fontSize: '0.7rem', color: '#999' }}>
                          {loc.lat.toFixed(5)}, {loc.lng.toFixed(5)}
                        </span>
                      </div>
                    </div>
                  </Popup>
                </Marker>
              );
            })}
          </MapContainer>

          {/* Floating zoom controls replacement */}
          <div style={styles.mapAttrib}>
            OpenStreetMap
          </div>
        </div>

        {/* ── Members Panel ── */}
        <aside style={{ ...styles.panel, display: panelOpen ? 'flex' : 'none' }}>
          <div style={styles.panelHeader}>
            <h2 style={styles.panelTitle}>Members ({memberList.length})</h2>
            <span style={styles.onlineTag}>{onlineCount} online</span>
          </div>

          <div style={styles.memberList}>
            {memberList.length === 0 && (
              <div style={styles.emptyPanel}>
                <span style={{ fontSize: 32 }}>📡</span>
                <p>No members have shared their location yet.</p>
                <p style={{ fontSize: '0.78rem', marginTop: 4 }}>
                  Press <strong>Share Location</strong> to start.
                </p>
              </div>
            )}

            {/* Sort: online first, then self first */}
            {[...memberList]
              .sort((a, b) => {
                if (a.userId === user?.userId) return -1;
                if (b.userId === user?.userId) return 1;
                if (a.status === 'ONLINE' && b.status !== 'ONLINE') return -1;
                if (b.status === 'ONLINE' && a.status !== 'ONLINE') return 1;
                return 0;
              })
              .map(loc => (
                <MemberRow
                  key={loc.userId}
                  loc={loc}
                  isSelf={loc.userId === user?.userId}
                  color={colorMap[loc.userId] || PALETTE[0]}
                  onClick={() => setFocusTarget(loc)}
                />
              ))}
          </div>
        </aside>
      </div>
    </div>
  );
}

/* ─── MemberRow ─── */
function MemberRow({ loc, isSelf, color, onClick }) {
  const statusClass = {
    ONLINE:  'badge-online',
    OFFLINE: 'badge-offline',
    NO_GPS:  'badge-no-gps',
  }[loc.status] || 'badge-offline';

  return (
    <div className="member-item" onClick={onClick} style={styles.memberItem}>
      {/* Color avatar */}
      <div style={{ ...styles.avatar, background: color }}>
        {(loc.userName || '?').slice(0, 1).toUpperCase()}
      </div>

      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontWeight: 600, fontSize: '0.9rem', display: 'flex', alignItems: 'center', gap: 6 }}>
          {loc.userName}
          {isSelf && <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)', fontWeight: 400 }}>(you)</span>}
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginTop: 3, flexWrap: 'wrap' }}>
          <span className={`badge ${statusClass}`}>
            <span className="badge-dot" />
            {loc.status}
          </span>
          {loc.timestamp > 0 && (
            <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>
              {relTime(loc.timestamp)}
            </span>
          )}
        </div>
        {loc.lat !== 0 && (
          <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)', marginTop: 2 }}>
            {loc.lat.toFixed(4)}, {loc.lng.toFixed(4)}
          </div>
        )}
      </div>

      {loc.status === 'ONLINE' && loc.lat !== 0 && (
        <button
          style={styles.pinBtn}
          title="Pan to member"
          onClick={e => { e.stopPropagation(); onClick(); }}
        >
          📍
        </button>
      )}
    </div>
  );
}

/* ─── Styles ─── */
const styles = {
  page: {
    height: '100vh',
    display: 'flex',
    flexDirection: 'column',
    overflow: 'hidden',
    background: 'var(--bg)',
  },
  header: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: '10px 20px',
    borderBottom: '1px solid var(--border)',
    background: 'var(--bg-card)',
    flexShrink: 0,
    gap: 12,
    flexWrap: 'wrap',
  },
  groupName: { fontSize: '1.05rem', fontWeight: 800 },
  code: {
    background: 'var(--primary-light)',
    color: 'var(--primary)',
    borderRadius: 6,
    padding: '2px 8px',
    fontSize: '0.72rem',
    fontWeight: 700,
    letterSpacing: '0.1em',
  },
  onlinePill: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: 5,
    background: 'rgba(16,185,129,0.1)',
    color: 'var(--secondary)',
    borderRadius: 20,
    padding: '2px 10px',
    fontSize: '0.72rem',
    fontWeight: 700,
  },
  onlineDot: {
    width: 6,
    height: 6,
    borderRadius: '50%',
    background: 'var(--secondary)',
  },
  pollLabel: {
    fontSize: '0.72rem',
    color: 'var(--text-muted)',
    display: 'none',   // shown via CSS @media in style block below
  },
  gpsBar: {
    padding: '6px 20px',
    fontSize: '0.82rem',
    flexShrink: 0,
  },
  body: {
    flex: 1,
    display: 'flex',
    overflow: 'hidden',
    position: 'relative',
  },
  mapWrapper: {
    flex: 1,
    position: 'relative',
    overflow: 'hidden',
  },
  mapAttrib: {
    display: 'none',
  },
  panel: {
    width: 300,
    flexShrink: 0,
    background: 'var(--bg-card)',
    borderLeft: '1px solid var(--border)',
    flexDirection: 'column',
    overflow: 'hidden',
  },
  panelHeader: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: '14px 18px 10px',
    borderBottom: '1px solid var(--border)',
    flexShrink: 0,
  },
  panelTitle: {
    fontSize: '0.8rem',
    fontWeight: 700,
    color: 'var(--text-secondary)',
    textTransform: 'uppercase',
    letterSpacing: '0.08em',
  },
  onlineTag: {
    fontSize: '0.72rem',
    fontWeight: 700,
    color: 'var(--secondary)',
    background: 'rgba(16,185,129,0.1)',
    borderRadius: 20,
    padding: '2px 8px',
  },
  memberList: {
    flex: 1,
    overflowY: 'auto',
    padding: '4px 10px',
  },
  memberItem: {
    display: 'flex',
    alignItems: 'center',
    gap: 10,
    borderBottom: '1px solid var(--border)',
  },
  avatar: {
    width: 36,
    height: 36,
    borderRadius: '50%',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontWeight: 700,
    fontSize: '0.9rem',
    flexShrink: 0,
    color: '#fff',
  },
  pinBtn: {
    background: 'none',
    border: 'none',
    cursor: 'pointer',
    fontSize: '1rem',
    padding: '4px',
    flexShrink: 0,
  },
  emptyPanel: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    gap: 8,
    padding: '40px 16px',
    textAlign: 'center',
    color: 'var(--text-secondary)',
    fontSize: '0.85rem',
  },
  popup: {
    display: 'flex',
    alignItems: 'flex-start',
    gap: 8,
    padding: '4px 2px',
    minWidth: 140,
  },
  popupDot: {
    width: 10,
    height: 10,
    borderRadius: '50%',
    marginTop: 4,
    flexShrink: 0,
  },
};
