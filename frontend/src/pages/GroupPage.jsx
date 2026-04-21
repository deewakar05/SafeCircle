import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { groupApi } from '../services/api';
import { useAuth } from '../context/AuthContext';

export default function GroupPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const initMode = searchParams.get('mode') || 'create';

  const [mode, setMode] = useState(initMode);
  const [form, setForm] = useState({ name: '', threshold: 300, code: '' });
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleChange = e => setForm({ ...form, [e.target.name]: e.target.value });

  const handleCreate = async (e) => {
    e.preventDefault();
    setError(''); setLoading(true);
    try {
      const res = await groupApi.create({ name: form.name, distanceThreshold: Number(form.threshold) });
      setResult(res.data);
      // Save group id to localStorage
      const existing = JSON.parse(localStorage.getItem('sc_groups') || '[]');
      if (!existing.includes(res.data.id)) {
        localStorage.setItem('sc_groups', JSON.stringify([...existing, res.data.id]));
      }
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to create group');
    } finally {
      setLoading(false);
    }
  };

  const handleJoin = async (e) => {
    e.preventDefault();
    setError(''); setLoading(true);
    try {
      const res = await groupApi.join({ inviteCode: form.code.toUpperCase() });
      setResult(res.data);
      const existing = JSON.parse(localStorage.getItem('sc_groups') || '[]');
      if (!existing.includes(res.data.id)) {
        localStorage.setItem('sc_groups', JSON.stringify([...existing, res.data.id]));
      }
    } catch (err) {
      setError(err.response?.data?.message || 'Invalid invite code');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={styles.page}>
      <div style={styles.container}>
        {/* Back */}
        <button id="back-btn" className="btn btn-ghost" onClick={() => navigate('/dashboard')}
          style={{ marginBottom: 24, width: 'fit-content', padding: '8px 16px' }}>
          ← Dashboard
        </button>

        <h1 style={styles.title}>{mode === 'create' ? 'Create a Group' : 'Join a Group'}</h1>

        {/* Mode Toggle */}
        <div style={styles.tabs}>
          <button id="tab-create" className={`btn ${mode === 'create' ? 'btn-primary' : 'btn-ghost'}`}
            onClick={() => { setMode('create'); setResult(null); setError(''); }}
            style={{ flex: 1 }}>
            Create Group
          </button>
          <button id="tab-join" className={`btn ${mode === 'join' ? 'btn-primary' : 'btn-ghost'}`}
            onClick={() => { setMode('join'); setResult(null); setError(''); }}
            style={{ flex: 1 }}>
            Join Group
          </button>
        </div>

        {/* Form */}
        {!result && (
          <div className="card" style={{ marginTop: 24 }}>
            {mode === 'create' ? (
              <form onSubmit={handleCreate} style={styles.form}>
                <div className="input-group">
                  <label>Group Name</label>
                  <input className="input" id="group-name-input" type="text" name="name"
                    placeholder="e.g. Trip Goa 2025" value={form.name} onChange={handleChange} required />
                </div>
                <div className="input-group">
                  <label>Distance Alert Threshold (metres)</label>
                  <input className="input" id="threshold-input" type="number" name="threshold"
                    min="50" max="10000" value={form.threshold} onChange={handleChange} required />
                  <span style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>
                    Members beyond this distance will trigger an alert
                  </span>
                </div>
                {error && <p className="text-danger" style={{ fontSize: '0.85rem' }}>{error}</p>}
                <button id="create-submit-btn" type="submit" className="btn btn-primary btn-full btn-lg"
                  disabled={loading}>
                  {loading ? <><span className="spinner" style={{width:16,height:16}} /> Creating…</> : 'Create Group'}
                </button>
              </form>
            ) : (
              <form onSubmit={handleJoin} style={styles.form}>
                <div className="input-group">
                  <label>Invite Code</label>
                  <input className="input" id="invite-code-input" type="text" name="code"
                    placeholder="e.g. ABC123" value={form.code} onChange={handleChange}
                    maxLength={6} style={{ letterSpacing: '0.2em', fontSize: '1.2rem', fontWeight: 700 }}
                    required />
                </div>
                {error && <p className="text-danger" style={{ fontSize: '0.85rem' }}>{error}</p>}
                <button id="join-submit-btn" type="submit" className="btn btn-secondary btn-full btn-lg"
                  disabled={loading}>
                  {loading ? <><span className="spinner" style={{width:16,height:16}} /> Joining…</> : 'Join Group'}
                </button>
              </form>
            )}
          </div>
        )}

        {/* Success State */}
        {result && (
          <div className="card" style={{ marginTop: 24, textAlign: 'center' }}>
            <div style={{ fontSize: 48, marginBottom: 12 }}>
              {mode === 'create' ? '🎉' : '✅'}
            </div>
            <h2 style={{ fontSize: '1.3rem', fontWeight: 800, marginBottom: 8 }}>{result.name}</h2>
            {mode === 'create' && (
              <>
                <p className="text-muted" style={{ marginBottom: 16 }}>Share this code with your group members</p>
                <div style={styles.codeBox}>
                  <span style={styles.codeText}>{result.inviteCode}</span>
                  <button id="copy-code-btn" className="btn btn-ghost"
                    style={{ padding: '8px 14px' }}
                    onClick={() => navigator.clipboard.writeText(result.inviteCode)}>
                    📋 Copy
                  </button>
                </div>
              </>
            )}
            <p className="text-muted" style={{ marginBottom: 20 }}>
              {result.memberIds?.length || 0} member(s) · Threshold: {result.distanceThreshold}m
            </p>
            <div style={{ display: 'flex', gap: 12 }}>
              <button id="go-dashboard-btn" className="btn btn-ghost" style={{ flex: 1 }}
                onClick={() => navigate('/dashboard')}>Dashboard</button>
              <button id="go-track-btn" className="btn btn-primary" style={{ flex: 1 }}
                onClick={() => navigate(`/track/${result.id}`)}>📍 Start Tracking</button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

const styles = {
  page: { minHeight: '100vh', padding: '32px 24px' },
  container: { maxWidth: 480, margin: '0 auto' },
  title: { fontSize: '1.6rem', fontWeight: 800, marginBottom: 20 },
  tabs: {
    display: 'flex', gap: 8,
    background: 'var(--bg-card)', padding: 6,
    borderRadius: 'var(--radius)', border: '1px solid var(--border)',
  },
  form: { display: 'flex', flexDirection: 'column', gap: 18 },
  codeBox: {
    display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 12,
    background: 'var(--primary-light)', borderRadius: 12,
    padding: '16px 24px', marginBottom: 16,
    border: '1px solid rgba(37,99,235,0.3)',
  },
  codeText: {
    fontSize: '2rem', fontWeight: 900, letterSpacing: '0.25em',
    color: 'var(--primary)', fontFamily: 'monospace',
  },
};
