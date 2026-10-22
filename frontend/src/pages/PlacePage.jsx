import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { errorMessage, getPlace, getPlaceTypes, reviewPlace } from '../api.js';
import { useAuth } from '../AuthContext.jsx';
import { formatScore, reviewCountLabel, StarInput, Stars } from '../components/StarRating.jsx';
import { formatDate, Honeypot, PhotoGrid, PhotoUploader } from '../components/Common.jsx';
import usePageMeta from '../usePageMeta.js';

/**
 * A dorm, dining hall, course, campus job or club. The review form is built from the
 * place type's questions, which come from the backend (GET /api/place-types).
 */
function PlaceReviewForm({ place, typeInfo, onSaved }) {
  const [ratings, setRatings] = useState({});
  const [extras, setExtras] = useState({});
  const [body, setBody] = useState('');
  const [website, setWebsite] = useState('');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    const missing = typeInfo.ratings.find((r) => !ratings[r.key]);
    if (missing) {
      setError(`Please rate "${missing.label}".`);
      return;
    }
    if (body.trim().length < 20) {
      setError('Please write at least 20 characters.');
      return;
    }
    setSaving(true);
    setError('');
    try {
      await reviewPlace(place.id, { ratings, extras, body, website });
      onSaved();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <form className="card form" onSubmit={handleSubmit}>
      <h2>Rate this {typeInfo.singular}</h2>
      {typeInfo.ratings.map((r) => (
        <StarInput
          key={r.key}
          label={r.label}
          value={ratings[r.key] || 0}
          onChange={(v) => setRatings({ ...ratings, [r.key]: v })}
        />
      ))}
      <div className="field-row">
        {typeInfo.extras.map((x) => (
          <label key={x.key} className="field">
            <span className="small">
              {x.label} <span className="muted">(optional)</span>
            </span>
            {x.kind === 'yesno' ? (
              <select value={extras[x.key] || ''} onChange={(e) => setExtras({ ...extras, [x.key]: e.target.value })}>
                <option value="">–</option>
                <option value="yes">Yes</option>
                <option value="no">No</option>
              </select>
            ) : (
              <input
                type={x.kind === 'number' ? 'number' : 'text'}
                min={x.kind === 'number' ? 0 : undefined}
                step={x.kind === 'number' ? 'any' : undefined}
                maxLength={100}
                value={extras[x.key] || ''}
                onChange={(e) => setExtras({ ...extras, [x.key]: e.target.value })}
              />
            )}
          </label>
        ))}
      </div>
      <label className="field">
        <span>Your review</span>
        <textarea rows={4} maxLength={1500} value={body} onChange={(e) => setBody(e.target.value)} />
      </label>
      <Honeypot value={website} onChange={setWebsite} />
      {error && <div className="alert">{error}</div>}
      <div className="form-actions">
        <button type="submit" className="button" disabled={saving}>
          {saving ? 'Saving…' : 'Post review'}
        </button>
      </div>
    </form>
  );
}

export default function PlacePage() {
  const { id } = useParams();
  const { user } = useAuth();
  const [detail, setDetail] = useState(null);
  const [types, setTypes] = useState([]);
  const [error, setError] = useState('');

  function load() {
    getPlace(id)
      .then(setDetail)
      .catch((e) => setError(errorMessage(e)));
  }

  useEffect(load, [id]);
  useEffect(() => {
    getPlaceTypes().then(setTypes).catch(() => setTypes([]));
  }, []);

  const metaPlace = detail?.place;
  usePageMeta({
    title: metaPlace ? `${metaPlace.name} at ${detail.universityName}: student reviews` : null,
    description: metaPlace
      ? `Student reviews and ratings of ${metaPlace.name} at ${detail.universityName}.` +
        (metaPlace.description ? ` ${metaPlace.description}.` : '')
      : null,
  });

  if (error && !detail) return <div className="alert">{error}</div>;
  if (!detail) return <div className="loading">Loading…</div>;

  const { place } = detail;
  const typeInfo = types.find((t) => t.type === place.type);
  const reviewedByMe = detail.reviews.some((r) => r.mine);
  const labelFor = (key, list) => list?.find((f) => f.key === key)?.label || key;

  return (
    <>
      <p className="breadcrumb">
        <Link to={`/universities/${place.universityId}`}>{detail.universityName}</Link>
        {typeInfo && (
          <>
            {' › '}
            <Link to={`/universities/${place.universityId}?tab=${place.type}`}>{typeInfo.label}</Link>
          </>
        )}
      </p>
      <section className="school-header">
        <div>
          <h1>{place.name}</h1>
          {place.description && <p className="muted">{place.description}</p>}
        </div>
      </section>

      <section className="card school-summary">
        <div className="overall">
          <div className="overall-score">{formatScore(place.avgRating)}</div>
          {place.reviewCount > 0 && <Stars value={place.avgRating} />}
          <div className="muted small">{reviewCountLabel(place.reviewCount)}</div>
        </div>
        <div className="rating-bars">
          {Object.entries(detail.averages).map(([key, value]) => (
            <div className="rating-bar" key={key}>
              <span className="rating-bar-label">{labelFor(key, typeInfo?.ratings)}</span>
              <span className="rating-bar-track">
                <span className="rating-bar-fill" style={{ width: `${(value / 5) * 100}%` }} />
              </span>
              <span className="rating-bar-value">{formatScore(value)}</span>
            </div>
          ))}
        </div>
      </section>

      <PhotoGrid photos={detail.photos} canReport={Boolean(user)} />

      {user && typeInfo && !reviewedByMe && <PlaceReviewForm place={place} typeInfo={typeInfo} onSaved={load} />}
      {user && (
        <PhotoUploader
          universityId={place.universityId}
          placeId={place.id}
          label={`Add a photo of ${place.name}`}
          onUploaded={(photo) => photo.status === 'APPROVED' && load()}
        />
      )}
      {!user && (
        <p className="muted">
          <Link to="/login">Log in</Link> to rate {place.name}.
        </p>
      )}

      <section>
        <h2 className="section-title">Reviews</h2>
        {detail.reviews.length === 0 && <div className="card empty">No reviews yet.</div>}
        {detail.reviews.map((r) => (
          <article key={r.id ?? 'mine'} className="card review">
            <header className="review-header">
              <div className="review-score">{r.rating.toFixed(1)}</div>
              <div>
                <strong>{r.authorName}</strong>
                <div className="muted small">{formatDate(r.createdAt)}</div>
              </div>
            </header>
            <p className="review-body">{r.body}</p>
            <dl className="review-scores">
              {Object.entries(r.ratings).map(([key, value]) => (
                <div key={key}>
                  <dt>{labelFor(key, typeInfo?.ratings)}</dt>
                  <dd>
                    <Stars value={value} />
                  </dd>
                </div>
              ))}
            </dl>
            {Object.keys(r.extras).length > 0 && (
              <p className="extras small">
                {Object.entries(r.extras).map(([key, value]) => (
                  <span key={key} className="badge">
                    {labelFor(key, typeInfo?.extras)}: {value}
                  </span>
                ))}
              </p>
            )}
          </article>
        ))}
      </section>
    </>
  );
}
