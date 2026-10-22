import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { errorMessage } from '../api.js';
import { useAuth } from '../AuthContext.jsx';
import usePageMeta from '../usePageMeta.js';

export default function LoginPage() {
  usePageMeta({ title: 'Log in', noindex: true });
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [form, setForm] = useState({ email: '', password: '' });
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setSaving(true);
    setError('');
    try {
      await login(form);
      // Go back to where the user was heading before we asked them to log in.
      navigate(location.state?.from || '/', { replace: true });
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="narrow auth">
      <h1>Log in</h1>
      {error && <div className="alert">{error}</div>}
      <form className="card form" onSubmit={handleSubmit}>
        <label className="field">
          <span>University email</span>
          <input
            type="email"
            required
            autoComplete="email"
            value={form.email}
            onChange={(e) => setForm({ ...form, email: e.target.value })}
          />
        </label>
        <label className="field">
          <span>Password</span>
          <input
            type="password"
            required
            autoComplete="current-password"
            value={form.password}
            onChange={(e) => setForm({ ...form, password: e.target.value })}
          />
        </label>
        <button type="submit" className="button button-block" disabled={saving}>
          {saving ? 'Logging in…' : 'Log in'}
        </button>
      </form>
      <p className="muted center">
        New here? <Link to="/register" state={location.state}>Create an account</Link>
      </p>
    </div>
  );
}
