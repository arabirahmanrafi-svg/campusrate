import { useCallback, useEffect, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import {
  errorMessage,
  fileUrl,
  getAdminStats,
  getFlaggedReviews,
  getPendingItems,
  moderate,
  sendDigest,
} from '../api.js';
import { useAuth } from '../AuthContext.jsx';
import { BarChart } from '../components/Charts.jsx';
import { formatDate } from '../components/Common.jsx';
import usePageMeta from '../usePageMeta.js';

const STAT_TILES = [
  ['users', 'Students'],
  ['reviews', 'Reviews'],
  ['activeUsersLast7Days', 'Active this week'],
  ['places', 'Places'],
  ['questions', 'Questions'],
  ['photos', 'Photos'],
];

/** Admin dashboard: site numbers plus the moderation queues. */
export default function AdminPage() {
  usePageMeta({ title: 'Admin', noindex: true });
  const { user, checking } = useAuth();
  const [stats, setStats] = useState(null);
  const [flags, setFlags] = useState([]);
  const [pending, setPending] = useState(null);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  const load = useCallback(() => {
    Promise.all([getAdminStats(), getFlaggedReviews(), getPendingItems()])
      .then(([s, f, p]) => {
        setStats(s);
        setFlags(f);
        setPending(p);
      })
      .catch((e) => setError(errorMessage(e)));
  }, []);

  useEffect(() => {
    if (user?.role === 'ADMIN') load();
  }, [user, load]);

  if (checking) return <div className="loading">Loading…</div>;
  if (!user) return <Navigate to="/login" state={{ from: '/admin' }} replace />;
  if (user.role !== 'ADMIN') return <div className="alert">Admins only.</div>;

  async function act(kind, id, action) {
    try {
      await moderate(kind, id, action);
      load();
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  async function handleDigest() {
    try {
      const result = await sendDigest();
      setMessage(`Weekly digest sent: ${result.sent} notifications.`);
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  return (
    <>
      <div className="section-header">
        <h1>Admin dashboard</h1>
        <button type="button" className="button button-secondary button-small" onClick={handleDigest}>
          Send weekly digest now
        </button>
      </div>
      {message && <div className="notice">{message}</div>}
      {error && <div className="alert">{error}</div>}

      {stats && (
        <>
          <div className="stat-tiles">
            {STAT_TILES.map(([key, label]) => (
              <div key={key} className="card stat">
                <div className="stat-value">{stats[key]}</div>
                <div className="muted small">{label}</div>
              </div>
            ))}
          </div>
          <section className="card">
            <h2>Reviews per day, last 30 days</h2>
            <BarChart
              data={stats.reviewsPerDay.map((d) => ({ label: d.day, value: d.count }))}
              ariaLabel="Reviews written per day over the last 30 days"
            />
          </section>
        </>
      )}

      <section>
        <h2 className="section-title">Flagged reviews ({flags.length})</h2>
        {flags.length === 0 && <div className="card empty">Nothing flagged.</div>}
        {flags.map((f) => (
          <article key={f.reviewId} className="card review">
            <div className="muted small">
              <Link to={`/universities/${f.universityId}`}>{f.universityName}</Link> · {f.authorName} ({f.authorEmail}) ·{' '}
              {formatDate(f.createdAt)} · rated {f.overall.toFixed(1)}
            </div>
            <ul className="reasons">
              {f.reasons.map((r) => (
                <li key={r}>{r}</li>
              ))}
            </ul>
            <p className="review-body">{f.body}</p>
            <div className="form-actions">
              <button type="button" className="button button-secondary" onClick={() => act('reviews', f.reviewId, 'remove')}>
                Remove
              </button>
              <button type="button" className="button" onClick={() => act('reviews', f.reviewId, 'approve')}>
                Looks genuine, publish
              </button>
            </div>
          </article>
        ))}
      </section>

      {pending && (
        <>
          <section>
            <h2 className="section-title">Photos waiting for approval ({pending.photos.length})</h2>
            <div className="photo-grid">
              {pending.photos.map((p) => (
                <figure key={p.id} className="photo">
                  <img src={fileUrl(p.url)} alt={p.caption || 'Pending photo'} />
                  <figcaption>
                    <span>{p.caption || ' '}</span>
                    <span className="muted small">by {p.uploaderName}</span>
                    <span>
                      <button type="button" className="link-button danger" onClick={() => act('photos', p.id, 'reject')}>
                        Reject
                      </button>{' '}
                      <button type="button" className="link-button" onClick={() => act('photos', p.id, 'approve')}>
                        Approve
                      </button>
                    </span>
                  </figcaption>
                </figure>
              ))}
            </div>
          </section>

          <section>
            <h2 className="section-title">Suggested places ({pending.places.length})</h2>
            {pending.places.map((p) => (
              <div key={p.id} className="card pending-row">
                <span>
                  <strong>{p.name}</strong> <span className="badge">{p.type}</span>{' '}
                  <Link to={`/universities/${p.universityId}`} className="small">
                    View school
                  </Link>
                  {p.description && <div className="muted small">{p.description}</div>}
                </span>
                <span>
                  <button type="button" className="link-button danger" onClick={() => act('places', p.id, 'reject')}>
                    Reject
                  </button>{' '}
                  <button type="button" className="link-button" onClick={() => act('places', p.id, 'approve')}>
                    Approve
                  </button>
                </span>
              </div>
            ))}
          </section>

          <section>
            <h2 className="section-title">Suggested community links ({pending.links.length})</h2>
            {pending.links.map((l) => (
              <div key={l.id} className="card pending-row">
                <span>
                  <strong>{l.name}</strong> <span className="badge">{l.kind}</span>
                  {l.url && (
                    <div className="small">
                      <a href={l.url} target="_blank" rel="noreferrer">
                        {l.url}
                      </a>
                    </div>
                  )}
                  {l.description && <div className="muted small">{l.description}</div>}
                </span>
                <span>
                  <button type="button" className="link-button danger" onClick={() => act('links', l.id, 'reject')}>
                    Reject
                  </button>{' '}
                  <button type="button" className="link-button" onClick={() => act('links', l.id, 'approve')}>
                    Approve
                  </button>
                </span>
              </div>
            ))}
          </section>
        </>
      )}
    </>
  );
}
