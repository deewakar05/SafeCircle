import { useEffect, useState, useRef, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { MapContainer, TileLayer, Marker, Popup, CircleMarker, Polyline, useMap, useMapEvents } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { groupApi, locationApi } from '../services/api';
import { useAuth } from '../context/AuthContext';
import { useWebSocket } from '../hooks/useWebSocket';
import { useLocationSharing } from '../hooks/useLocationSharing';

/* ─── Leaflet icon fix for Vite ─── */
delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon-2x.png',
  iconUrl:       'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon.png',
  shadowUrl:     'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
});

/* ─── Color palette ─── */
const PALETTE = [
  '#2563EB', '#10B981', '#F59E0B', '#EF4444',
  '#8B5CF6', '#06B6D4', '#EC4899', '#14B8A6',
];

/* ─── Simple equirectangular distance (metres) ─── */
function approxDist(lat1, lng1, lat2, lng2) {
  const R  = 6_371_000;
  const dL = (lat2 - lat1) * Math.PI / 180;
  const dx = (lng2 - lng1) * Math.PI / 180 * Math.cos(((lat1 + lat2) / 2) * Math.PI / 180);
  return Math.sqrt(dL * dL + dx * dx) * R;
}

/* ─── Relative time ─── */
function relTime(ts, now = Date.now()) {
  if (!ts) return '';
  const s = Math.floor((now - ts) / 1000);
  if (s < 10) return 'just now';
  if (s < 60) return `${s}s ago`;
  const m = Math.floor(s / 60);
  return m < 60 ? `${m}m ago` : `${Math.floor(m / 60)}h ago`;
}

/* ─── Army-style custom marker ─── */
function makeMarkerIcon(color, initials, isSelf, isPulsing) {
  const body   = isSelf ? 44 : 38;
  const total  = body + 32; // room for pulse ring
  const half   = total / 2;
  const glow   = isSelf
    ? `box-shadow:0 0 0 3px rgba(255,255,255,0.25),0 4px 20px ${color}99;`
    : `box-shadow:0 2px 10px ${color}66;`;

  const ring = isPulsing ? `
    <div style="
      position:absolute;top:50%;left:50%;
      width:${body + 8}px;height:${body + 8}px;
      margin-left:-${(body + 8) / 2}px;margin-top:-${(body + 8) / 2}px;
      border:2.5px solid ${color};border-radius:50%;
      animation:marker-pulse 1.6s ease-out infinite;
    "></div>` : '';

  const html = `
    <div style="position:relative;width:${total}px;height:${total}px;
                display:flex;align-items:center;justify-content:center;">
      ${ring}
      <div style="
        width:${body}px;height:${body}px;border-radius:50%;
        background:linear-gradient(135deg,${color}cc,${color});
        display:flex;align-items:center;justify-content:center;
        font-family:-apple-system,sans-serif;font-weight:800;
        font-size:${isSelf ? 15 : 13}px;color:#fff;letter-spacing:-0.5px;
        border:2.5px solid rgba(255,255,255,0.25);
        ${glow}position:relative;z-index:1;
      ">${initials}</div>
      ${isSelf ? `<div style="position:absolute;bottom:${(total - body) / 2 - 6}px;
        left:50%;transform:translateX(-50%);
        width:8px;height:8px;background:${color};border-radius:50%;
        border:2px solid #0f1117;z-index:2;"></div>` : ''}
    </div>`;

  return L.divIcon({
    html,
    className: '',
    iconSize:   [total, total],
    iconAnchor: [half, half],
    popupAnchor:[0, -(body / 2 + 4)],
  });
}

/* ─── Checkpoint Icon ─── */
function makeCheckpointIcon(index) {
  const html = `
    <div style="width:28px;height:28px;border-radius:50%;
                background:var(--primary);border:2px solid #fff;
                display:flex;align-items:center;justify-content:center;
                font-weight:bold;color:#fff;font-size:12px;
                box-shadow:0 2px 8px rgba(0,0,0,0.4);">
      ${index + 1}
    </div>`;
  return L.divIcon({ html, className: '', iconSize: [28, 28], iconAnchor: [14, 14] });
}

/* ─── Map controller: fit bounds + flyTo ─── */
function MapController({ members, focusTarget, setFocusTarget }) {
  const map = useMap();

  useMapEvents({
    dragstart() {
      if (focusTarget) setFocusTarget(null);
    }
  });

  // Fit all online markers on first meaningful load
  const fittedRef = useRef(false);
  useEffect(() => {
    if (fittedRef.current) return;
    const online = Object.values(members).filter(m => m.lat !== 0 && m.lng !== 0);
    if (online.length === 0) return;
    fittedRef.current = true;
    map.fitBounds(L.latLngBounds(online.map(m => [m.lat, m.lng])).pad(0.3),
      { maxZoom: 16, animate: true });
  }, [members, map]);

  // Pan smoothly when a member is clicked
  useEffect(() => {
    if (focusTarget?.lat && focusTarget.lat !== 0) {
      map.flyTo([focusTarget.lat, focusTarget.lng], 16, { duration: 1.1 });
    }
  }, [focusTarget, map]);

  return null;
}

/* ─── OSRM Route Fetcher ─── */
async function fetchOSRMRoute(checkpoints) {
  if (checkpoints.length < 2) return [];
  const coords = checkpoints.map(c => `${c.lng},${c.lat}`).join(';');
  try {
    const res = await fetch(`https://router.project-osrm.org/route/v1/driving/${coords}?overview=full&geometries=geojson`);
    const data = await res.json();
    if (data.routes && data.routes.length > 0) {
      return data.routes[0].geometry.coordinates.map(([lng, lat]) => [lat, lng]); // Leaflet uses [lat, lng]
    }
  } catch (err) {
    console.error('OSRM fetch error:', err);
  }
  // Fallback to straight lines if OSRM fails
  return checkpoints.map(c => [c.lat, c.lng]);
}

/* ─── Map Events for Route Editing ─── */
function RouteEditor({ isEditing, onAddPoint }) {
  useMapEvents({
    click(e) {
      if (isEditing) {
        onAddPoint({ lat: e.latlng.lat, lng: e.latlng.lng, name: `Point` });
      }
    }
  });
  return null;
}

/* ─── WS Status config ─── */
const WS_CFG = {
  CONNECTED:    { label: 'LIVE',    dot: '#10B981', bg: 'rgba(16,185,129,0.12)',  color: '#10B981', pulse: true },
  CONNECTING:   { label: 'SYNC…',  dot: '#F59E0B', bg: 'rgba(245,158,11,0.12)', color: '#F59E0B', pulse: false },
  DISCONNECTED: { label: 'OFFLINE', dot: '#EF4444', bg: 'rgba(239,68,68,0.12)',  color: '#EF4444', pulse: false },
};

/* ════════════════════════════════════
   Main Component
   ════════════════════════════════════ */
export default function TrackingPage() {
  const { groupId } = useParams();
  const { user }    = useAuth();
  const navigate    = useNavigate();

  const [group,       setGroup]       = useState(null);
  const [members,     setMembers]     = useState({});
  const [trails,      setTrails]      = useState({});  // userId → [[lat,lng],…]
  const [speeds,      setSpeeds]      = useState({});  // userId → km/h
  const [alerts,      setAlerts]      = useState([]);
  const [focusTarget, setFocusTarget] = useState(null);
  const [colorMap,    setColorMap]    = useState({});
  const [panelOpen,   setPanelOpen]   = useState(true);
  const [lastPoll,    setLastPoll]    = useState(null);
  const [now,         setNow]         = useState(Date.now());

  // Route state
  const [isEditingRoute, setIsEditingRoute] = useState(false);
  const [routePoints, setRoutePoints]       = useState([]);
  const [osrmPath, setOsrmPath]             = useState([]);
  const [routeSaving, setRouteSaving]       = useState(false);

  const alertTimers   = useRef([]);
  const prevMembers   = useRef({});  // tracks previous positions for speed calc

  /* ── Assign stable colors ── */
  const assignColor = useCallback((uid) => {
    setColorMap(prev => {
      if (prev[uid]) return prev;
      return { ...prev, [uid]: PALETTE[Object.keys(prev).length % PALETTE.length] };
    });
  }, []);

  /* ── Location update handler (from WS) ── */
  const handleLocation = useCallback((loc) => {
    assignColor(loc.userId);

    // Speed calculation from previous position
    const prev = prevMembers.current[loc.userId];
    if (prev && prev.lat !== 0 && loc.lat !== 0 && prev.timestamp && loc.timestamp) {
      const dist = approxDist(prev.lat, prev.lng, loc.lat, loc.lng);
      const dt   = (loc.timestamp - prev.timestamp) / 1000;
      if (dt > 0.5 && dt < 120) {
        setSpeeds(s => ({ ...s, [loc.userId]: Math.round(dist / dt * 3.6) }));
      }
    }
    prevMembers.current = { ...prevMembers.current, [loc.userId]: loc };

    // Breadcrumb trail
    if (loc.lat !== 0 && loc.lng !== 0) {
      setTrails(t => {
        const arr = t[loc.userId] || [];
        return { ...t, [loc.userId]: [...arr, [loc.lat, loc.lng]].slice(-8) };
      });
    }

    setMembers(m => ({ ...m, [loc.userId]: loc }));
  }, [assignColor]);

  /* ── Alert handler ── */
  const handleAlert = useCallback((msg) => {
    const id = Date.now();
    setAlerts(a => [{ id, msg }, ...a.slice(0, 4)]);
    const t = setTimeout(() => setAlerts(a => a.filter(x => x.id !== id)), 6000);
    alertTimers.current.push(t);
  }, []);

  /* ── WebSocket ── */
  const { wsState, publish } = useWebSocket(groupId, handleLocation, handleAlert);

  /* ── GPS sharing ── */
  const { sharing, accuracy, gpsError, startSharing, stopSharing } =
    useLocationSharing(groupId, publish);

  /* ── Polling (initial load + fallback) ── */
  useEffect(() => {
    const fetchAll = () => {
      groupApi.get(groupId).then(r => {
        setGroup(r.data);
        if (r.data.route && r.data.route.length > 0 && !isEditingRoute) {
          setRoutePoints(r.data.route);
        }
      }).catch(() => {});
      locationApi.getGroup(groupId).then(r => {
        r.data.forEach(loc => {
          assignColor(loc.userId);
          prevMembers.current[loc.userId] = loc;
        });
        setMembers(m => {
          const next = { ...m };
          r.data.forEach(loc => { next[loc.userId] = loc; });
          return next;
        });
        setLastPoll(Date.now());
      }).catch(() => {});
    };
    fetchAll();
    const id = setInterval(fetchAll, 60_000); // 60s fallback polling instead of 10s to save battery/bandwidth
    return () => clearInterval(id);
  }, [groupId, assignColor, isEditingRoute]);

  /* ── Fetch OSRM geometry when routePoints change ── */
  useEffect(() => {
    let active = true;
    if (routePoints.length > 1) {
      fetchOSRMRoute(routePoints).then(path => {
        if (active) setOsrmPath(path);
      });
    } else {
      setOsrmPath([]);
    }
    return () => { active = false; };
  }, [routePoints]);

  /* ── 1-second heartbeat for live timestamps ── */
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, []);

  /* ── Cleanup ── */
  useEffect(() => () => alertTimers.current.forEach(clearTimeout), []);

  /* ── Derived ── */
  const memberList  = Object.values(members);
  const onlineCount = memberList.filter(m => m.status === 'ONLINE').length;
  const wsCfg       = WS_CFG[wsState] || WS_CFG.CONNECTING;

  const locateMe = () => {
    if (user?.userId && members[user.userId]) setFocusTarget(members[user.userId]);
  };

  const isAdmin = user?.userId && group?.adminId === user.userId;

  const handleSaveRoute = async () => {
    setRouteSaving(true);
    try {
      await groupApi.updateRoute(groupId, { checkpoints: routePoints });
      handleAlert('✅ Route saved successfully');
      setIsEditingRoute(false);
    } catch (err) {
      handleAlert('❌ Failed to save route');
    } finally {
      setRouteSaving(false);
    }
  };

  return (
    <div style={S.page}>
      {/* ── Toast overlay ── */}
      {alerts.length > 0 && (
        <div className="toast-container">
          {alerts.map(a => <div key={a.id} className="toast">{a.msg}</div>)}
        </div>
      )}

      <header style={S.header}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, minWidth: 0 }}>
          <button id="back-btn" className="btn btn-ghost"
            style={{ padding: '6px 14px', fontSize: '0.82rem', flexShrink: 0 }}
            onClick={() => navigate('/dashboard')}>← Back</button>

          <div style={{ minWidth: 0 }}>
            <h1 style={S.groupName}>{group?.name || 'Loading…'}</h1>
            <div style={{ display: 'flex', gap: 6, alignItems: 'center', marginTop: 2, flexWrap: 'wrap' }}>
              {/* WS status pill */}
              <span style={{ ...S.wsPill, background: wsCfg.bg, color: wsCfg.color, border: `1px solid ${wsCfg.dot}44` }}>
                <span style={{
                  ...S.wsDot,
                  background: wsCfg.dot,
                  animation: wsCfg.pulse ? 'pulse 1.5s infinite' : 'none',
                }} />
                {wsCfg.label}
              </span>

              {group?.inviteCode && (
                <span style={S.codeTag}>{group.inviteCode}</span>
              )}
              <span style={S.onlinePill}>
                <span style={S.onlineDot} />
                {onlineCount} online
              </span>
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 }}>
          {lastPoll && (
            <span style={S.pollLabel}>↻ {relTime(lastPoll)}</span>
          )}
          <button id="locate-me-btn" className="btn btn-ghost"
            style={{ padding: '8px 12px' }} onClick={locateMe} title="Centre on me">
            🎯
          </button>
          <button
            id={sharing ? 'stop-sharing-btn' : 'start-sharing-btn'}
            className={`btn ${sharing ? 'btn-danger' : 'btn-secondary'}`}
            onClick={sharing ? stopSharing : startSharing}
            style={{ padding: '10px 20px' }}>
            {sharing ? '⏹ Stop' : '📡 Share'}
          </button>
          {isAdmin && !isEditingRoute && (
            <button className="btn btn-secondary" style={{ padding: '8px 12px' }}
              onClick={() => setIsEditingRoute(true)}>
              🗺️ Edit Route
            </button>
          )}
          <button id="toggle-panel-btn" className="btn btn-ghost"
            style={{ padding: '8px 12px' }}
            onClick={() => setPanelOpen(v => !v)} title="Toggle panel">
            👥
          </button>
        </div>
      </header>

      {/* ── Route Edit Toolbar ── */}
      {isEditingRoute && (
        <div style={S.routeToolbar}>
          <span style={{ fontSize: '0.85rem', fontWeight: 600 }}>🗺️ Route Edit Mode: Click on the map to add checkpoints</span>
          <div style={{ display: 'flex', gap: 8 }}>
            <button className="btn btn-ghost" style={{ padding: '4px 10px', fontSize: '0.8rem' }}
              onClick={() => { setRoutePoints([]); setOsrmPath([]); }}>
              🗑️ Clear
            </button>
            <button className="btn btn-ghost" style={{ padding: '4px 10px', fontSize: '0.8rem' }}
              onClick={() => { setIsEditingRoute(false); setRoutePoints(group?.route || []); }}>
              Cancel
            </button>
            <button className="btn btn-primary" style={{ padding: '4px 14px', fontSize: '0.8rem' }}
              onClick={handleSaveRoute} disabled={routeSaving}>
              {routeSaving ? 'Saving...' : '💾 Save Route'}
            </button>
          </div>
        </div>
      )}

      {/* ── GPS status bar ── */}
      {(sharing || gpsError) && (
        <div style={{
          ...S.gpsBar,
          background: gpsError ? 'rgba(239,68,68,0.1)' : 'rgba(16,185,129,0.07)',
          borderBottom: `1px solid ${gpsError ? 'rgba(239,68,68,0.2)' : 'rgba(16,185,129,0.18)'}`,
        }}>
          {gpsError
            ? <span style={{ color: 'var(--danger)' }}>⚠️ GPS: {gpsError}</span>
            : <span style={{ color: 'var(--secondary)' }}>
                📡 Sharing · ±{accuracy ?? '…'}m · via {wsState === 'CONNECTED' ? 'WebSocket' : 'HTTP'}
              </span>}
        </div>
      )}

      <div style={S.body}>

        {/* ── Map ── */}
        <div style={S.mapWrapper}>
          <MapContainer
            center={[28.6139, 77.2090]} zoom={13}
            style={{ width: '100%', height: '100%' }}
            zoomControl={false}>
            <TileLayer
              attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
              url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
              className="map-tiles" />

            <MapController members={members} focusTarget={focusTarget} setFocusTarget={setFocusTarget} />
            <RouteEditor isEditing={isEditingRoute} onAddPoint={p => setRoutePoints(prev => [...prev, p])} />

            {/* Render Route Polyline */}
            {osrmPath.length > 0 && (
              <Polyline positions={osrmPath} pathOptions={{ color: 'var(--primary)', weight: 5, opacity: 0.8 }} />
            )}
            
            {/* Render Route Checkpoints */}
            {routePoints.map((pt, idx) => (
              <Marker key={`cp-${idx}`} position={[pt.lat, pt.lng]} icon={makeCheckpointIcon(idx)}>
                <Popup>
                  <div style={{ padding: '2px 4px', fontWeight: 600 }}>Checkpoint {idx + 1}</div>
                </Popup>
              </Marker>
            ))}

            {memberList.map(loc => {
              if (loc.lat === 0 && loc.lng === 0) return null;
              const isSelf    = loc.userId === user?.userId;
              const color     = colorMap[loc.userId] || PALETTE[0];
              const initials  = (loc.userName || '?').slice(0, 2).toUpperCase();
              const isPulsing = loc.timestamp && (Date.now() - loc.timestamp) < 5000;
              const trail     = trails[loc.userId] || [];

              return (
                <span key={loc.userId}>
                  {/* Breadcrumb trail dots */}
                  {trail.slice(0, -1).map(([tlat, tlng], idx) => (
                    <CircleMarker key={`t-${loc.userId}-${idx}`}
                      center={[tlat, tlng]} radius={3}
                      pathOptions={{
                        color: color, fillColor: color,
                        fillOpacity: ((idx + 1) / trail.length) * 0.45,
                        opacity: 0, weight: 0,
                      }} />
                  ))}

                  {/* Accuracy circle (own marker only) */}
                  {isSelf && accuracy && (
                    <CircleMarker center={[loc.lat, loc.lng]} radius={0}
                      pathOptions={{ color, fillColor: color, fillOpacity: 0.07, weight: 1 }}>
                    </CircleMarker>
                  )}

                  <Marker
                    position={[loc.lat, loc.lng]}
                    icon={makeMarkerIcon(color, initials, isSelf, isPulsing)}>
                    <Popup>
                      <div style={S.popup}>
                        <div style={{ ...S.popupDot, background: color }} />
                        <div>
                          <strong style={{ display: 'block', fontSize: '0.9rem' }}>
                            {loc.userName}{isSelf ? ' (you)' : ''}
                          </strong>
                          <span style={{ fontSize: '0.75rem', color: '#555' }}>
                            {loc.status} · {relTime(loc.timestamp, now)}
                          </span>
                          {speeds[loc.userId] > 0 && (
                            <span style={{ display: 'block', fontSize: '0.72rem', color: '#777', marginTop: 2 }}>
                              ⚡ {speeds[loc.userId]} km/h
                            </span>
                          )}
                          <span style={{ display: 'block', fontSize: '0.68rem', color: '#999', marginTop: 2 }}>
                            {loc.lat.toFixed(5)}, {loc.lng.toFixed(5)}
                          </span>
                        </div>
                      </div>
                    </Popup>
                  </Marker>
                </span>
              );
            })}
          </MapContainer>
        </div>

        {panelOpen && (
          <aside style={S.panel}>
            <div style={S.panelHead}>
              <span style={S.panelTitle}>Members ({memberList.length})</span>
              <span style={S.onlineTag}>{onlineCount} online</span>
            </div>

            <div style={S.memberList}>
              {memberList.length === 0 && (
                <div style={S.emptyPanel}>
                  <span style={{ fontSize: 36 }}>📡</span>
                  <p>No members yet.</p>
                  <p style={{ fontSize: '0.78rem', marginTop: 4 }}>
                    Press <strong>Share</strong> to start.
                  </p>
                </div>
              )}

              {[...memberList]
                .sort((a, b) => {
                  if (a.userId === user?.userId) return -1;
                  if (b.userId === user?.userId) return 1;
                  if (a.status === 'ONLINE' && b.status !== 'ONLINE') return -1;
                  if (b.status === 'ONLINE' && a.status !== 'ONLINE') return 1;
                  return (b.timestamp || 0) - (a.timestamp || 0);
                })
                .map(loc => (
                  <MemberRow key={loc.userId} loc={loc}
                    isSelf={loc.userId === user?.userId}
                    color={colorMap[loc.userId] || PALETTE[0]}
                    speed={speeds[loc.userId]}
                    onClick={() => setFocusTarget(loc)} />
                ))}
            </div>
          </aside>
        )}
      </div>
    </div>
  );
}

/* ─── Member Row ─── */
function MemberRow({ loc, isSelf, color, speed, onClick }) {
  const statusClass = {
    ONLINE: 'badge-online', OFFLINE: 'badge-offline', NO_GPS: 'badge-no-gps',
  }[loc.status] || 'badge-offline';

  const isPulsing = loc.timestamp && (Date.now() - loc.timestamp) < 5000;

  return (
    <div className="member-item" onClick={onClick} style={S.memberItem}>
      {/* Avatar with optional pulse ring */}
      <div style={{ position: 'relative', flexShrink: 0 }}>
        {isPulsing && (
          <div style={{
            position: 'absolute', inset: -4,
            border: `2px solid ${color}`,
            borderRadius: '50%',
            animation: 'member-pulse 1.8s ease-out infinite',
          }} />
        )}
        <div style={{ ...S.avatar, background: color }}>
          {(loc.userName || '?').slice(0, 1).toUpperCase()}
        </div>
      </div>

      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontWeight: 600, fontSize: '0.88rem', display: 'flex', alignItems: 'center', gap: 5 }}>
          <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {loc.userName}
          </span>
          {isSelf && <span style={{ fontSize: '0.68rem', color: 'var(--text-muted)', fontWeight: 400 }}>(you)</span>}
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginTop: 3, flexWrap: 'wrap' }}>
          <span className={`badge ${statusClass}`}>
            <span className="badge-dot" />{loc.status}
          </span>
          {loc.timestamp > 0 && (
            <span style={{ fontSize: '0.68rem', color: 'var(--text-muted)' }}>
              {relTime(loc.timestamp, now)}
            </span>
          )}
        </div>
        {/* Speed badge */}
        {speed > 0 && loc.status === 'ONLINE' && (
          <div style={S.speedBadge}>⚡ {speed} km/h</div>
        )}
        {/* Coordinates */}
        {loc.lat !== 0 && (
          <div style={{ fontSize: '0.67rem', color: 'var(--text-muted)', marginTop: 2 }}>
            {loc.lat.toFixed(4)}, {loc.lng.toFixed(4)}
          </div>
        )}
      </div>

      {loc.status === 'ONLINE' && loc.lat !== 0 && (
        <button style={S.pinBtn} title="Fly to" onClick={e => { e.stopPropagation(); onClick(); }}>
          📍
        </button>
      )}
    </div>
  );
}

/* ─── Styles ─── */
const S = {
  page:       { height: '100vh', display: 'flex', flexDirection: 'column', overflow: 'hidden' },
  header:     {
    display: 'flex', alignItems: 'center', justifyContent: 'space-between',
    padding: '10px 18px', borderBottom: '1px solid var(--border)',
    background: 'var(--bg-card)', flexShrink: 0, gap: 12, flexWrap: 'wrap',
  },
  groupName:  { fontSize: '1rem', fontWeight: 800, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' },
  wsPill:     {
    display: 'inline-flex', alignItems: 'center', gap: 5,
    borderRadius: 20, padding: '2px 10px',
    fontSize: '0.7rem', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.08em',
  },
  wsDot:      { width: 7, height: 7, borderRadius: '50%', flexShrink: 0 },
  codeTag:    {
    background: 'var(--primary-light)', color: 'var(--primary)',
    borderRadius: 6, padding: '2px 8px', fontSize: '0.72rem', fontWeight: 700, letterSpacing: '0.1em',
  },
  onlinePill: {
    display: 'inline-flex', alignItems: 'center', gap: 5,
    background: 'rgba(16,185,129,0.1)', color: 'var(--secondary)',
    borderRadius: 20, padding: '2px 9px', fontSize: '0.7rem', fontWeight: 700,
  },
  onlineDot:  { width: 6, height: 6, borderRadius: '50%', background: 'var(--secondary)' },
  pollLabel:  { fontSize: '0.7rem', color: 'var(--text-muted)' },
  gpsBar:     { padding: '5px 18px', fontSize: '0.8rem', flexShrink: 0 },
  body:       { flex: 1, display: 'flex', overflow: 'hidden' },
  mapWrapper: { flex: 1, overflow: 'hidden' },
  panel:      {
    width: 295, flexShrink: 0, background: 'var(--bg-card)',
    borderLeft: '1px solid var(--border)', display: 'flex', flexDirection: 'column', overflow: 'hidden',
  },
  panelHead:  {
    display: 'flex', alignItems: 'center', justifyContent: 'space-between',
    padding: '13px 16px 10px', borderBottom: '1px solid var(--border)', flexShrink: 0,
  },
  panelTitle: { fontSize: '0.78rem', fontWeight: 700, color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '0.08em' },
  onlineTag:  { fontSize: '0.7rem', fontWeight: 700, color: 'var(--secondary)', background: 'rgba(16,185,129,0.1)', borderRadius: 20, padding: '2px 8px' },
  memberList: { flex: 1, overflowY: 'auto', padding: '4px 8px' },
  memberItem: { display: 'flex', alignItems: 'center', gap: 10, borderBottom: '1px solid var(--border)' },
  avatar:     {
    width: 36, height: 36, borderRadius: '50%',
    display: 'flex', alignItems: 'center', justifyContent: 'center',
    fontWeight: 800, fontSize: '0.9rem', color: '#fff', flexShrink: 0,
  },
  speedBadge: {
    fontSize: '0.68rem', color: 'var(--warning)',
    background: 'rgba(245,158,11,0.1)', borderRadius: 4,
    padding: '1px 6px', marginTop: 3, display: 'inline-block', fontWeight: 700,
  },
  pinBtn:     { background: 'none', border: 'none', cursor: 'pointer', fontSize: '1rem', padding: '4px', flexShrink: 0 },
  emptyPanel: {
    display: 'flex', flexDirection: 'column', alignItems: 'center',
    gap: 8, padding: '36px 16px', textAlign: 'center',
    color: 'var(--text-secondary)', fontSize: '0.85rem',
  },
  popup:      { display: 'flex', alignItems: 'flex-start', gap: 8, padding: '4px 2px', minWidth: 140 },
  popupDot:   { width: 10, height: 10, borderRadius: '50%', marginTop: 4, flexShrink: 0 },
  routeToolbar: {
    background: 'var(--primary-light)', padding: '8px 18px', display: 'flex',
    alignItems: 'center', justifyContent: 'space-between', borderBottom: '1px solid var(--border)', flexShrink: 0
  }
};
