import { useEffect, useState } from 'react';
import { Navigate } from 'react-router-dom';
import { errorMessage, fieldErrors, updateProfile } from '../api.js';
import { useAuth } from '../AuthContext.jsx';
import { STATUS_LABELS } from '../components/Common.jsx';

export default function ProfilePage() {
  const { user, checking, setUser } = useAuth();
  const [form, setForm] = useState(null);
  const [errors, setErrors] = useState({});
  const [message, setMessage] = useState('');

  useEffect(() => {
    if (user) {
      setForm({
        displayName: user.displayName,
        studentStatus: user.studentStatus,
        international: user.international,
        homeCountry: user.homeCountry || '',
        showCountry: user.showCountry,
      });
    }
  }, [user]);

  if (checking) return <div className="loading">Loading…</div>;
  if (!user) return <Navigate to="/login" state={{ from: '/me/profile' }} replace />;
  if (!form) return null;

  async function handleSubmit(e) {
    e.preventDefault();
    setErrors({});
    setMessage('');
    try {
      setUser(await updateProfile(form));
      setMessage('Saved.');
    } catch (err) {
      setErrors(fieldErrors(err));
      setMessage(errorMessage(err));
    }
  }

  return (
    <div className="narrow">
      <h1>Your profile</h1>
      <p className="muted">
        {user.email} · ★ {user.reputation} reputation
        {user.role === 'ADMIN' && ' · Admin'}
      </p>
      <form className="card form" onSubmit={handleSubmit}>
        <label className="field">
          <span>Display name</span>
          <input required maxLength={60} value={form.displayName} onChange={(e) => setForm({ ...form, displayName: e.target.value })} />
          {errors.displayName && <span className="field-error">{errors.displayName}</span>}
        </label>
        <label className="field">
          <span>I am a…</span>
          <select value={form.studentStatus} onChange={(e) => setForm({ ...form, studentStatus: e.target.value })}>
            {Object.entries(STATUS_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <label className="checkbox">
          <input type="checkbox" checked={form.international} onChange={(e) => setForm({ ...form, international: e.target.checked })} />
          I'm an international student
        </label>
        <label className="field">
          <span>
            Home country <span className="muted small">(optional)</span>
          </span>
          <input maxLength={80} value={form.homeCountry} onChange={(e) => setForm({ ...form, homeCountry: e.target.value })} />
        </label>
        <label className="checkbox">
          <input
            type="checkbox"
            checked={form.showCountry}
            disabled={!form.homeCountry.trim()}
            onChange={(e) => setForm({ ...form, showCountry: e.target.checked })}
          />
          Show my home country on my reviews (so students from the same country can find them)
        </label>
        <p className="muted small">Your home country is private unless you tick the box above.</p>
        <div className="form-actions">
          {message && <span className="muted small">{message}</span>}
          <button type="submit" className="button">
            Save
          </button>
        </div>
      </form>
    </div>
  );
}
