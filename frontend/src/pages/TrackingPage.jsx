import { useEffect, useState, useRef, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { MapContainer, TileLayer, Marker, Popup, useMap } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { groupApi, locationApi } from '../services/api';
import { connectSocket, disconnectSocket } from '../services/socket';
import { useAuth } from '../context/AuthContext';

// Fix for default marker icons in Leaflet + Vite/Webpack
delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon-2x.png',
  iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
});

const MAP_CENTER = [28.6139, 77.2090]; // [lat, lng]

function RecenterMap({ center }) {
  const map = useMap();
  useEffect(() => {
    map.setView(center);
  }, [center, map]);
  return null;
}

export default function TrackingPage() {
  const { groupId } = useParams();
  const { user } = useAuth();
  const navigate = useNavigate();

  const [group, setGroup] = useState(null);
  const [members, setMembers] = useState({});   // userId → LocationResponse
  const [alerts, setAlerts] = useState([]);
  const [selected, setSelected] = useState(null);
  const [mapCenter, setMapCenter] = useState(MAP_CENTER);
  const [sharing, setSharing] = useState(false);

  const watchIdRef = useRef(null);
  const alertTimers = useRef([]);

  // Load and Pool group info
  useEffect(() => {
    const fetchAll = () => {
      groupApi.get(groupId).then(r => setGroup(r.data)).catch(() => {});
      locationApi.getGroup(groupId).then(r => {
        const map = {};
        r.data.forEach(loc => { map[loc.userId] = loc; });
        setMembers(map);
      }).catch(() => {});
    };

    fetchAll();
    const interval = setInterval(fetchAll, 10000); // 10s Polling fallback
    return () => clearInterval(interval);
  }, [groupId]);

  // WebSocket connection
  useEffect(() => {
    connectSocket(
      groupId,
      (loc) => {
        setMembers(prev => ({ ...prev, [loc.userId]: loc }));
      },
      (alertMsg) => {
        const id = Date.now();
        setAlerts(prev => [{ id, msg: alertMsg }, ...prev.slice(0, 4)]);
        const t = setTimeout(() => setAlerts(prev => prev.filter(a => a.id !== id)), 6000);
        alertTimers.current.push(t);
      }
    );
    return () => {
      disconnectSocket();
      alertTimers.current.forEach(clearTimeout);
    };
  }, [groupId]);

  // GPS sharing
  const startSharing = useCallback(() => {
    if (!navigator.geolocation) return;
    setSharing(true);
    const sendUpdate = (pos) => {
      const { latitude: lat, longitude: lng } = pos.coords;
      setMapCenter({ lat, lng });
      locationApi.update({ groupId, lat, lng, status: 'ONLINE' }).catch(() => {});
    };
    watchIdRef.current = navigator.geolocation.watchPosition(
      sendUpdate,
      () => locationApi.update({ groupId, lat: 0, lng: 0, status: 'NO_GPS' }).catch(() => {}),
      { enableHighAccuracy: true, maximumAge: 5000, timeout: 10000 }
    );
  }, [groupId]);

  const stopSharing = useCallback(() => {
    if (watchIdRef.current != null) {
      navigator.geolocation.clearWatch(watchIdRef.current);
      watchIdRef.current = null;
    }
    setSharing(false);
    locationApi.update({ groupId, lat: 0, lng: 0, status: 'OFFLINE' }).catch(() => {});
  }, [groupId]);

  useEffect(() => () => { if (watchIdRef.current != null) stopSharing(); }, [stopSharing]);

  const memberList = Object.values(members);

  return (
    <div style={styles.page}>
      {/* Header */}
      <header style={styles.header}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <button id="back-to-dashboard-btn" className="btn btn-ghost"
            style={{ padding: '6px 12px', fontSize: '0.85rem' }}
            onClick={() => navigate('/dashboard')}>← Back</button>
          <div>
            <h1 style={styles.groupName}>{group?.name || 'Loading…'}</h1>
            <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginTop: 2 }}>
              <span className="live-badge">Live</span>
              {group?.inviteCode && (
                <span style={styles.code}>{group.inviteCode}</span>
              )}
            </div>
          </div>
        </div>
        <button
          id={sharing ? 'stop-sharing-btn' : 'start-sharing-btn'}
          className={`btn ${sharing ? 'btn-danger' : 'btn-secondary'}`}
          onClick={sharing ? stopSharing : startSharing}
          style={{ padding: '10px 20px' }}
        >
          {sharing ? '⏹ Stop' : '📡 Share Location'}
        </button>
      </header>

      {/* Main layout: Map + Panel */}
      <div style={styles.body}>
        {/* Map */}
        <div style={styles.mapWrapper}>
          <MapContainer
            center={mapCenter}
            zoom={13}
            style={{ width: '100%', height: '100%', background: '#0F1117' }}
            zoomControl={false}
          >
            <TileLayer
              attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
              url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
              className="map-tiles"
            />
            
            {memberList.map(loc => loc.lat !== 0 && (
              <Marker
                key={loc.userId}
                position={[loc.lat, loc.lng]}
              >
                <Popup>
                  <div style={{ color: '#111', padding: '4px 8px' }}>
                    <strong style={{ display: 'block', fontSize: '0.9rem' }}>{loc.userName}</strong>
                    <span style={{ fontSize: '0.8rem', color: '#666' }}>{loc.status}</span>
                  </div>
                </Popup>
              </Marker>
            ))}
            
            <RecenterMap center={mapCenter} />
          </MapContainer>
        </div>

        {/* Members Panel */}
        <aside style={styles.panel}>
          <h2 style={styles.panelTitle}>Members ({memberList.length})</h2>
          <div style={styles.memberList}>
            {memberList.length === 0 && (
              <p style={{ color: 'var(--text-muted)', fontSize: '0.875rem', textAlign: 'center', padding: '20px 0' }}>
                No members online yet
              </p>
            )}
            {memberList.map(loc => (
              <MemberItem key={loc.userId} loc={loc}
                isSelf={loc.userId === user?.userId}
                onClick={() => setMapCenter({ lat: loc.lat, lng: loc.lng })}
              />
            ))}
          </div>

          {/* Alerts */}
          {alerts.length > 0 && (
            <div style={styles.alertBox}>
              <h3 style={styles.alertTitle}>⚠️ Alerts</h3>
              {alerts.map(a => (
                <div key={a.id} style={styles.alertItem}>{a.msg}</div>
              ))}
            </div>
          )}
        </aside>
      </div>
    </div>
  );
}

function MemberItem({ loc, isSelf, onClick }) {
  const statusClass = {
    ONLINE: 'badge-online', 
    OFFLINE: 'badge-offline', 
    NO_GPS: 'badge-no-gps',
  }[loc.status] || 'badge-offline';

  return (
    <div style={memberItemStyle} onClick={onClick} className="member-item">
      <div style={styles.memberAvatar}>{loc.userName?.[0]?.toUpperCase() || '?'}</div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontWeight: 600, fontSize: '0.9rem' }}>
          {loc.userName}{isSelf && <span style={{ color: 'var(--text-muted)', fontWeight: 400 }}> (you)</span>}
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginTop: 4 }}>
          <span className={`badge ${statusClass}`}>
            <span className="badge-dot" />
            {loc.status}
          </span>
          {loc.lat !== 0 && (
            <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>
               · {loc.lat.toFixed(4)}, {loc.lng.toFixed(4)}
            </span>
          )}
        </div>
      </div>
      {loc.status === 'ONLINE' && loc.lat !== 0 && (
        <button style={styles.locBtn} title="Centre on map" onClick={(e) => { e.stopPropagation(); onClick(); }}>
          📍
        </button>
      )}
    </div>
  );
}

const memberItemStyle = {
  display: 'flex', alignItems: 'center', gap: 12,
  padding: '10px 0', borderBottom: '1px solid var(--border)',
  cursor: 'pointer',
};

const styles = {
  page: { height: '100vh', display: 'flex', flexDirection: 'column', overflow: 'hidden' },
  header: {
    display: 'flex', alignItems: 'center', justifyContent: 'space-between',
    padding: '12px 24px', borderBottom: '1px solid var(--border)',
    background: 'var(--bg-card)', flexShrink: 0,
  },
  groupName: { fontSize: '1.1rem', fontWeight: 800 },
  code: {
    background: 'var(--primary-light)', color: 'var(--primary)',
    borderRadius: 6, padding: '2px 8px',
    fontSize: '0.75rem', fontWeight: 700, letterSpacing: '0.1em',
  },
  body: { flex: 1, display: 'flex', overflow: 'hidden' },
  mapWrapper: { flex: 1, position: 'relative' },
  noMap: {
    height: '100%', display: 'flex', flexDirection: 'column',
    alignItems: 'center', justifyContent: 'center', gap: 12,
    background: 'var(--bg)', position: 'relative',
  },
  mockMapGrid: {
    position: 'absolute', inset: 0,
    backgroundImage: 'linear-gradient(var(--border) 1px, transparent 1px), linear-gradient(90deg, var(--border) 1px, transparent 1px)',
    backgroundSize: '40px 40px', opacity: 0.5, zIndex: -1,
  },
  panel: {
    width: 300, flexShrink: 0,
    background: 'var(--bg-card)', borderLeft: '1px solid var(--border)',
    display: 'flex', flexDirection: 'column', overflow: 'hidden',
  },
  panelTitle: {
    padding: '16px 20px 12px',
    fontSize: '0.85rem', fontWeight: 700,
    color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '0.08em',
    borderBottom: '1px solid var(--border)',
  },
  memberList: { flex: 1, overflowY: 'auto', padding: '0 20px' },
  memberAvatar: {
    width: 38, height: 38, borderRadius: '50%',
    background: 'linear-gradient(135deg, var(--primary), var(--secondary))',
    display: 'flex', alignItems: 'center', justifyContent: 'center',
    fontWeight: 700, fontSize: '0.9rem', flexShrink: 0,
  },
  locBtn: {
    background: 'none', border: 'none', cursor: 'pointer',
    fontSize: '1rem', padding: '4px',
  },
  alertBox: {
    padding: '16px 20px',
    borderTop: '1px solid var(--border)',
    background: 'rgba(245,158,11,0.05)',
  },
  alertTitle: {
    fontSize: '0.8rem', fontWeight: 700, color: 'var(--warning)',
    textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: 10,
  },
  alertItem: {
    fontSize: '0.82rem', color: 'var(--text-primary)',
    padding: '6px 0', borderBottom: '1px solid var(--border)',
    lineHeight: 1.4,
  },
};
