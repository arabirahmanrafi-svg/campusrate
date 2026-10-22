import { useEffect, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { errorMessage, getNotifications, markNotificationsRead } from '../api.js';
import { useAuth } from '../AuthContext.jsx';
import { formatDate } from '../components/Common.jsx';
import usePageMeta from '../usePageMeta.js';

export default function NotificationsPage() {
  usePageMeta({ title: 'Notifications', noindex: true });
  const { user, checking, refreshUnread } = useAuth();
  const [items, setItems] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!user) return;
    getNotifications()
      .then((data) => {
        setItems(data.items);
        // Opening this page marks everything as read.
        if (data.unread > 0) markNotificationsRead().then(refreshUnread).catch(() => {});
      })
      .catch((e) => setError(errorMessage(e)));
  }, [user, refreshUnread]);

  if (checking) return <div className="loading">Loading…</div>;
  if (!user) return <Navigate to="/login" state={{ from: '/me/notifications' }} replace />;

  return (
    <div className="narrow">
      <h1>Notifications</h1>
      <p className="muted small">Follow a university to get a weekly summary of new reviews and questions.</p>
      {error && <div className="alert">{error}</div>}
      {items && items.length === 0 && <div className="card empty">Nothing yet.</div>}
      {items?.map((n) => (
        <div key={n.id} className={`card notification ${n.read ? '' : 'unread'}`}>
          {n.link ? <Link to={n.link}>{n.message}</Link> : n.message}
          <div className="muted small">{formatDate(n.createdAt)}</div>
        </div>
      ))}
    </div>
  );
}
