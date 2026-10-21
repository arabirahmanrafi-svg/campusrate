import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { errorMessage, fieldErrors } from '../api.js';
import { useAuth } from '../AuthContext.jsx';
import { Honeypot } from '../components/Common.jsx';

export default function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [form, setForm] = useState({ displayName: '', email: '', password: '', website: '' });
  const [errors, setErrors] = useState({});
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  const set = (field) => (e) => setForm({ ...form, [field]: e.target.value });

  async function handleSubmit(e) {
    e.preventDefault();
    setSaving(true);
    setError('');
    setErrors({});
    try {
      await register(form);
      navigate(location.state?.from || '/', { replace: true });
    } catch (err) {
      setErrors(fieldErrors(err));
      setError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="narrow auth">
      <h1>Create your account</h1>
      <p className="muted">Use your university email so other students know reviews come from real students.</p>
      {error && <div className="alert">{error}</div>}
      <form className="card form" onSubmit={handleSubmit}>
        <label className="field">
          <span>Display name</span>
          <input required maxLength={60} value={form.displayName} onChange={set('displayName')} placeholder="Shown on your reviews" />
          {errors.displayName && <span className="field-error">{errors.displayName}</span>}
        </label>
        <label className="field">
          <span>University email</span>
          <input type="email" required autoComplete="email" value={form.email} onChange={set('email')} placeholder="you@university.edu" />
          {errors.email && <span className="field-error">{errors.email}</span>}
        </label>
        <label className="field">
          <span>Password</span>
          <input type="password" required minLength={8} autoComplete="new-password" value={form.password} onChange={set('password')} />
          <span className="muted small">At least 8 characters</span>
          {errors.password && <span className="field-error">{errors.password}</span>}
        </label>
        <Honeypot value={form.website} onChange={(website) => setForm({ ...form, website })} />
        <button type="submit" className="button button-block" disabled={saving}>
          {saving ? 'Creating account…' : 'Sign up'}
        </button>
      </form>
      <p className="muted center">
        Already have an account? <Link to="/login" state={location.state}>Log in</Link>
      </p>
    </div>
  );
}
