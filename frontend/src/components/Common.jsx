import { useState } from 'react';
import { errorMessage, fileUrl, reportPhoto, uploadPhoto } from '../api.js';

/** Turns "CURRENT" into "Current student" and so on. */
export const STATUS_LABELS = {
  CURRENT: 'Current student',
  ALUMNI: 'Alumni',
  TRANSFER: 'Transfer student',
  PROSPECTIVE: 'Prospective student',
};

/** Small labels next to a reviewer's name. */
export function AuthorBadges({ author }) {
  if (!author) return null;
  return (
    <span className="badges">
      <span className="badge">{STATUS_LABELS[author.studentStatus] || 'Student'}</span>
      {author.international && <span className="badge badge-intl">International</span>}
      {author.country && <span className="badge">From {author.country}</span>}
      {author.reputation > 0 && (
        <span className="badge" title="Reputation: helpful votes on this student's reviews and answers">
          ★ {author.reputation}
        </span>
      )}
    </span>
  );
}

/** A row of tab buttons. tabs: [{ key, label }] */
export function Tabs({ tabs, active, onChange, label }) {
  return (
    <div className="tabs" role="tablist" aria-label={label}>
      {tabs.map((t) => (
        <button
          key={t.key}
          type="button"
          role="tab"
          aria-selected={active === t.key}
          className={`tab ${active === t.key ? 'tab-on' : ''}`}
          onClick={() => onChange(t.key)}
        >
          {t.label}
        </button>
      ))}
    </div>
  );
}

/** Map of a campus, using OpenStreetMap's embeddable page (plain HTML, no map library). */
export function CampusMap({ latitude, longitude, name }) {
  if (latitude == null || longitude == null) return null;
  const d = 0.012;
  const bbox = [longitude - d, latitude - d * 0.6, longitude + d, latitude + d * 0.6].join(',');
  const src = `https://www.openstreetmap.org/export/embed.html?bbox=${bbox}&layer=mapnik&marker=${latitude},${longitude}`;
  return (
    <div className="map">
      <iframe title={`Map of ${name}`} src={src} loading="lazy" />
      <a
        className="small"
        href={`https://www.openstreetmap.org/?mlat=${latitude}&mlon=${longitude}#map=15/${latitude}/${longitude}`}
        target="_blank"
        rel="noreferrer"
      >
        View larger map
      </a>
    </div>
  );
}

/** A grid of photos with a report button on each. */
export function PhotoGrid({ photos, canReport = false, onReported }) {
  const [reported, setReported] = useState({});
  if (!photos?.length) return null;

  async function report(photo) {
    if (!window.confirm('Report this photo as inappropriate?')) return;
    try {
      await reportPhoto(photo.id);
      setReported((r) => ({ ...r, [photo.id]: true }));
      onReported?.(photo);
    } catch (e) {
      window.alert(errorMessage(e));
    }
  }

  return (
    <div className="photo-grid">
      {photos.map((p) => (
        <figure key={p.id} className="photo">
          <a href={fileUrl(p.url)} target="_blank" rel="noreferrer">
            <img src={fileUrl(p.url)} alt={p.caption || 'Student photo'} loading="lazy" />
          </a>
          <figcaption>
            <span>{p.caption || ' '}</span>
            <span className="muted small">by {p.uploaderName}</span>
            {canReport && (
              <button type="button" className="link-button small" onClick={() => report(p)} disabled={reported[p.id]}>
                {reported[p.id] ? 'Reported' : 'Report'}
              </button>
            )}
          </figcaption>
        </figure>
      ))}
    </div>
  );
}

/** Pick a photo, add a caption and upload it. Tells the student if it's waiting for approval. */
export function PhotoUploader({ universityId, placeId, onUploaded, label = 'Add a photo' }) {
  const [file, setFile] = useState(null);
  const [caption, setCaption] = useState('');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    if (!file) return;
    if (file.size > 5 * 1024 * 1024) {
      setMessage('That photo is larger than 5 MB.');
      return;
    }
    setBusy(true);
    setMessage('');
    try {
      const photo = await uploadPhoto(universityId, file, { caption, placeId });
      setFile(null);
      setCaption('');
      e.target.reset();
      setMessage(
        photo.status === 'APPROVED'
          ? 'Photo added.'
          : 'Thanks! Your photo will appear after a moderator approves it.',
      );
      onUploaded?.(photo);
    } catch (err) {
      setMessage(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="card uploader" onSubmit={handleSubmit}>
      <strong>{label}</strong>
      <input type="file" accept="image/jpeg,image/png,image/webp" onChange={(e) => setFile(e.target.files[0] || null)} />
      <input
        type="text"
        maxLength={200}
        placeholder="Caption (optional)"
        value={caption}
        onChange={(e) => setCaption(e.target.value)}
      />
      <button type="submit" className="button button-small" disabled={!file || busy}>
        {busy ? 'Uploading…' : 'Upload'}
      </button>
      {message && <span className="muted small">{message}</span>}
    </form>
  );
}

/** Hidden "honeypot" field: invisible to people, but simple bots fill it in. */
export function Honeypot({ value, onChange }) {
  return (
    <div className="honeypot" aria-hidden="true">
      <label>
        Website
        <input type="text" tabIndex={-1} autoComplete="off" value={value} onChange={(e) => onChange(e.target.value)} />
      </label>
    </div>
  );
}

export function formatDate(isoString) {
  return new Date(isoString).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
}

export function formatMoney(value) {
  return value == null ? '–' : `$${Math.round(value).toLocaleString()}`;
}
