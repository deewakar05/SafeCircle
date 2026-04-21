import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { authApi } from '../services/api';
import { useAuth } from '../context/AuthContext';

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [isSignup, setIsSignup] = useState(false);
  const [form, setForm] = useState({ name: '', email: '', password: '' });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleChange = e => setForm({ ...form, [e.target.name]: e.target.value });

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const res = isSignup
        ? await authApi.signup(form)
        : await authApi.login({ email: form.email, password: form.password });
      const { token, userId, name, email } = res.data;
      login(token, { userId, name, email });
      navigate('/dashboard');
    } catch (err) {
      setError(err.response?.data?.message || 'Something went wrong. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={styles.page}>
      {/* Background glow */}
      <div style={styles.glow1} />
      <div style={styles.glow2} />

      <div style={styles.card}>
        {/* Logo */}
        <div style={styles.logo}>
          <div style={styles.logoIcon}>🛡️</div>
          <h1 style={styles.logoText}>SafeCircle</h1>
        </div>
        <p style={styles.subtitle}>
          {isSignup ? 'Create your account' : 'Welcome back — sign in to continue'}
        </p>

        <form onSubmit={handleSubmit} style={styles.form}>
          {isSignup && (
            <div className="input-group">
              <label>Full Name</label>
              <input
                className="input"
                id="name"
                type="text"
                name="name"
                placeholder="Deewakar Kumar"
                value={form.name}
                onChange={handleChange}
                required
              />
            </div>
          )}

          <div className="input-group">
            <label>Email</label>
            <input
              className="input"
              id="email"
              type="email"
              name="email"
              placeholder="you@example.com"
              value={form.email}
              onChange={handleChange}
              required
            />
          </div>

          <div className="input-group">
            <label>Password</label>
            <input
              className="input"
              id="password"
              type="password"
              name="password"
              placeholder={isSignup ? 'Min 6 characters' : '••••••••'}
              value={form.password}
              onChange={handleChange}
              required
            />
          </div>

          {error && <p className="text-danger" style={{ fontSize: '0.85rem' }}>{error}</p>}

          <button
            id="auth-submit-btn"
            type="submit"
            className="btn btn-primary btn-full btn-lg"
            disabled={loading}
            style={{ marginTop: 8 }}
          >
            {loading
              ? <><span className="spinner" style={{width:18,height:18}} /> Processing…</>
              : isSignup ? 'Create Account' : 'Sign In'}
          </button>
        </form>

        <p style={styles.toggle}>
          {isSignup ? 'Already have an account?' : "Don't have an account?"}{' '}
          <button
            id="auth-toggle-btn"
            type="button"
            onClick={() => { setIsSignup(!isSignup); setError(''); }}
            style={styles.toggleBtn}
          >
            {isSignup ? 'Sign In' : 'Create Account'}
          </button>
        </p>
      </div>
    </div>
  );
}

const styles = {
  page: {
    minHeight: '100vh',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    position: 'relative',
    overflow: 'hidden',
    padding: 24,
  },
  glow1: {
    position: 'absolute', width: 500, height: 500,
    borderRadius: '50%', top: '-10%', left: '-15%',
    background: 'radial-gradient(circle, rgba(37,99,235,0.15) 0%, transparent 70%)',
    pointerEvents: 'none',
  },
  glow2: {
    position: 'absolute', width: 400, height: 400,
    borderRadius: '50%', bottom: '-10%', right: '-10%',
    background: 'radial-gradient(circle, rgba(16,185,129,0.12) 0%, transparent 70%)',
    pointerEvents: 'none',
  },
  card: {
    background: 'var(--bg-card)',
    border: '1px solid var(--border)',
    borderRadius: 20,
    padding: '40px 36px',
    width: '100%',
    maxWidth: 420,
    boxShadow: 'var(--shadow)',
    position: 'relative',
    zIndex: 1,
  },
  logo: {
    display: 'flex', alignItems: 'center', gap: 12,
    marginBottom: 8,
  },
  logoIcon: { fontSize: 36 },
  logoText: {
    fontSize: '1.8rem', fontWeight: 800,
    background: 'linear-gradient(135deg, #60a5fa, #34d399)',
    WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent',
  },
  subtitle: {
    color: 'var(--text-secondary)', fontSize: '0.9rem',
    marginBottom: 28,
  },
  form: { display: 'flex', flexDirection: 'column', gap: 16 },
  toggle: {
    marginTop: 20, textAlign: 'center',
    color: 'var(--text-secondary)', fontSize: '0.875rem',
  },
  toggleBtn: {
    background: 'none', border: 'none', cursor: 'pointer',
    color: 'var(--primary)', fontWeight: 600, fontSize: '0.875rem',
    padding: 0,
  },
};
