import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { errorMessage, getDiningToday, getPlaces, suggestPlace } from '../../api.js';
import { useAuth } from '../../AuthContext.jsx';
import { formatScore, reviewCountLabel } from '../../components/StarRating.jsx';

const NAMES = {
  DORM: { plural: 'dorms', one: 'dorm', example: 'e.g. Walton Hall' },
  DINING: { plural: 'dining halls', one: 'dining hall', example: 'e.g. The Commons' },
  COURSE: { plural: 'courses', one: 'course', example: 'e.g. COSC 311 Data Structures' },
  JOB: { plural: 'campus jobs', one: 'campus job', example: 'e.g. Library front desk' },
  CLUB: { plural: 'clubs', one: 'club', example: 'e.g. Bangladesh Student Association' },
};

function WhatsGoodToday({ universityId }) {
  const [items, setItems] = useState([]);
  useEffect(() => {
    getDiningToday(universityId).then(setItems).catch(() => setItems([]));
  }, [universityId]);

  return (
    <section className="card today">
      <h2>What's good today</h2>
      {items.length === 0 ? (
        <p className="muted">No dining reviews in the last 24 hours. Ate somewhere today? Rate it below.</p>
      ) : (
        <ul className="today-list">
          {items.map((item, i) => (
            <li key={i}>
              <span className="today-score">{item.rating.toFixed(1)}</span>
              <div>
                <strong>{item.meal || 'A meal'}</strong> at <Link to={`/places/${item.placeId}`}>{item.placeName}</Link>
                <div className="muted small">{item.body}</div>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function SuggestPlace({ universityId, type, onAdded }) {
  const { user } = useAuth();
  const [open, setOpen] = useState(false);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [message, setMessage] = useState('');

  if (!user) {
    return (
      <p className="muted small">
        Missing one? <Link to="/login">Log in</Link> to suggest it.
      </p>
    );
  }
  if (!open) {
    return (
      <button type="button" className="link-button" onClick={() => setOpen(true)}>
        + Suggest a {NAMES[type].one} that's missing
      </button>
    );
  }

  async function handleSubmit(e) {
    e.preventDefault();
    try {
      const place = await suggestPlace(universityId, { type, name, description });
      setMessage(place.status === 'APPROVED' ? 'Added.' : 'Thanks! It will appear after a moderator approves it.');
      setName('');
      setDescription('');
      if (place.status === 'APPROVED') onAdded();
    } catch (err) {
      setMessage(errorMessage(err));
    }
  }

  return (
    <form className="card form compact" onSubmit={handleSubmit}>
      <label className="field">
        <span>Name</span>
        <input required maxLength={150} value={name} onChange={(e) => setName(e.target.value)} placeholder={NAMES[type].example} />
      </label>
      <label className="field">
        <span>
          Short description <span className="muted small">(optional)</span>
        </span>
        <input maxLength={500} value={description} onChange={(e) => setDescription(e.target.value)} />
      </label>
      <div className="form-actions">
        <button type="button" className="button button-secondary" onClick={() => setOpen(false)}>
          Cancel
        </button>
        <button type="submit" className="button">
          Suggest
        </button>
      </div>
      {message && <span className="muted small">{message}</span>}
    </form>
  );
}

/** The Dorms / Dining / Courses / Jobs / Clubs tabs. One component handles all five. */
export default function PlacesTab({ university, type }) {
  const [places, setPlaces] = useState(null);
  const [error, setError] = useState('');

  function load() {
    getPlaces(university.id, type)
      .then(setPlaces)
      .catch((e) => setError(errorMessage(e)));
  }

  useEffect(load, [university.id, type]);

  return (
    <>
      {type === 'DINING' && <WhatsGoodToday universityId={university.id} />}
      {error && <div className="alert">{error}</div>}
      {places && places.length === 0 && (
        <div className="card empty">No {NAMES[type].plural} listed yet for {university.name}.</div>
      )}
      <div className="place-list">
        {places?.map((p) => (
          <Link key={p.id} to={`/places/${p.id}`} className="card place-item">
            <div>
              <strong>{p.name}</strong>
              {p.description && <div className="muted small">{p.description}</div>}
            </div>
            <div className="place-score">
              <strong>{formatScore(p.avgRating)}</strong>
              <span className="muted small">{reviewCountLabel(p.reviewCount)}</span>
            </div>
          </Link>
        ))}
      </div>
      <SuggestPlace universityId={university.id} type={type} onAdded={load} />
    </>
  );
}
