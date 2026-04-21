import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { groupApi } from '../services/api';
import { useAuth } from '../context/AuthContext';

export default function DashboardPage() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [groups, setGroups] = useState([]);
  const [loading, setLoading] = useState(true);

  // In MVP, we'll store joined group IDs in localStorage
  useEffect(() => {
    const storedGroups = JSON.parse(localStorage.getItem('sc_groups') || '[]');
    const fetchAll = async () => {
      const fetched = [];
      for (const id of storedGroups) {
        try {
          const res = await groupApi.get(id);
          fetched.push(res.data);
        } catch {
          // stale id, skip
        }
      }
      setGroups(fetched);
      setLoading(false);
    };
    fetchAll();
  }, []);

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <div style={styles.page}>
      {/* Header */}
      <header style={styles.header}>
        <div style={styles.logoRow}>
          <span style={{ fontSize: 24 }}>🛡️</span>
          <span style={styles.brand}>SafeCircle</span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <div style={styles.avatar}>{user?.name?.[0]?.toUpperCase() || 'U'}</div>
          <button id="logout-btn" className="btn btn-ghost" onClick={handleLogout}
            style={{ padding: '8px 16px', fontSize: '0.8rem' }}>
            Sign Out
          </button>
        </div>
      </header>

      <main style={styles.main}>
        {/* Welcome */}
        <div style={styles.welcome}>
          <h1 style={styles.greeting}>Hello, {user?.name?.split(' ')[0] || 'Traveler'} 👋</h1>
          <p className="text-muted">Manage your travel groups and start tracking.</p>
        </div>

        {/* Quick Actions */}
        <div style={styles.actions}>
          <button id="create-group-btn" className="btn btn-primary btn-lg"
            onClick={() => navigate('/group?mode=create')}
            style={{ flex: 1 }}>
            ＋ Create Group
          </button>
          <button id="join-group-btn" className="btn btn-secondary btn-lg"
            onClick={() => navigate('/group?mode=join')}
            style={{ flex: 1 }}>
            🔑 Join Group
          </button>
        </div>

        {/* Groups List */}
        <div style={styles.section}>
          <h2 style={styles.sectionTitle}>Your Groups</h2>
          {loading ? (
            <div className="flex justify-center" style={{ padding: 40 }}>
              <div className="spinner" style={{ width: 32, height: 32 }} />
            </div>
          ) : groups.length === 0 ? (
            <div style={styles.emptyState}>
              <div style={{ fontSize: 48 }}>🗺️</div>
              <p>No groups yet. Create or join one to get started.</p>
            </div>
          ) : (
            <div style={styles.groupGrid}>
              {groups.map(g => (
                <GroupCard key={g.id} group={g} onTrack={() => navigate(`/track/${g.id}`)} />
              ))}
            </div>
          )}
        </div>
      </main>
    </div>
  );
}

function GroupCard({ group, onTrack }) {
  return (
    <div className="card" style={styles.groupCard}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <h3 style={{ fontSize: '1rem', fontWeight: 700, marginBottom: 4 }}>{group.name}</h3>
          <p className="text-muted">{group.memberIds?.length || 0} members</p>
        </div>
        <span style={styles.codeTag}>{group.inviteCode}</span>
      </div>
      <div style={{ marginTop: 16, display: 'flex', gap: 8 }}>
        <button id={`track-btn-${group.id}`} className="btn btn-primary"
          style={{ flex: 1, padding: '10px' }} onClick={onTrack}>
          📍 Live Track
        </button>
        <button
          className="btn btn-ghost"
          style={{ padding: '10px 14px' }}
          onClick={() => navigator.clipboard.writeText(group.inviteCode)}
          title="Copy invite code"
        >
          📋
        </button>
      </div>
    </div>
  );
}

const styles = {
  page: { minHeight: '100vh', display: 'flex', flexDirection: 'column' },
  header: {
    display: 'flex', alignItems: 'center', justifyContent: 'space-between',
    padding: '16px 24px',
    borderBottom: '1px solid var(--border)',
    background: 'rgba(26,29,46,0.8)',
    backdropFilter: 'blur(12px)',
    position: 'sticky', top: 0, zIndex: 100,
  },
  logoRow: { display: 'flex', alignItems: 'center', gap: 10 },
  brand: { fontSize: '1.2rem', fontWeight: 800, color: 'var(--text-primary)' },
  avatar: {
    width: 36, height: 36, borderRadius: '50%',
    background: 'linear-gradient(135deg, var(--primary), var(--secondary))',
    display: 'flex', alignItems: 'center', justifyContent: 'center',
    fontWeight: 700, fontSize: '0.9rem',
  },
  main: { flex: 1, maxWidth: 720, margin: '0 auto', padding: '32px 24px', width: '100%' },
  welcome: { marginBottom: 28 },
  greeting: { fontSize: '1.8rem', fontWeight: 800, marginBottom: 4 },
  actions: { display: 'flex', gap: 16, marginBottom: 36 },
  section: {},
  sectionTitle: { fontSize: '1rem', fontWeight: 700, color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '0.08em', marginBottom: 16 },
  emptyState: {
    textAlign: 'center', padding: '48px 24px',
    color: 'var(--text-secondary)',
    background: 'var(--bg-card)', borderRadius: 'var(--radius)',
    border: '1px dashed var(--border)',
    display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 12,
  },
  groupGrid: { display: 'flex', flexDirection: 'column', gap: 16 },
  groupCard: { transition: 'var(--transition)' },
  codeTag: {
    background: 'var(--primary-light)', color: 'var(--primary)',
    borderRadius: 6, padding: '3px 10px',
    fontSize: '0.8rem', fontWeight: 700, letterSpacing: '0.1em',
  },
};
