import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { errorMessage, getCountries, getRankings } from '../api.js';
import { CATEGORIES } from '../components/RatingBars.jsx';
import { formatScore } from '../components/StarRating.jsx';

const CATEGORY_OPTIONS = [
  { key: 'overall', label: 'Overall' },
  ...CATEGORIES,
  { key: 'international', label: 'International students' },
];

export default function RankingsPage() {
  const [category, setCategory] = useState('overall');
  const [country, setCountry] = useState('');
  const [countries, setCountries] = useState([]);
  const [rows, setRows] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    getCountries().then(setCountries).catch(() => setCountries([]));
  }, []);

  useEffect(() => {
    setRows(null);
    getRankings(category, country)
      .then(setRows)
      .catch((e) => setError(errorMessage(e)));
  }, [category, country]);

  return (
    <>
      <h1>University rankings</h1>
      <p className="muted">
        Ranked with a Bayesian average: schools with only a few reviews are pulled toward the site-wide average,
        so two perfect reviews can't beat hundreds of strong ones.
      </p>

      <div className="filters">
        <div className="tabs" role="tablist" aria-label="Category">
          {CATEGORY_OPTIONS.map((c) => (
            <button
              key={c.key}
              type="button"
              role="tab"
              aria-selected={category === c.key}
              className={`tab ${category === c.key ? 'tab-on' : ''}`}
              onClick={() => setCategory(c.key)}
            >
              {c.label}
            </button>
          ))}
        </div>
        <label className="sort">
          Country{' '}
          <select value={country} onChange={(e) => setCountry(e.target.value)}>
            <option value="">All countries</option>
            {countries.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </select>
        </label>
      </div>

      {error && <div className="alert">{error}</div>}
      {!rows && !error && <div className="loading">Loading…</div>}
      {rows && rows.length === 0 && (
        <div className="card empty">No reviewed universities here yet. Search for your school and add the first review.</div>
      )}

      {rows && rows.length > 0 && (
        <div className="card table-wrap">
          <table className="rankings">
            <thead>
              <tr>
                <th>#</th>
                <th>University</th>
                <th className="num">Average</th>
                <th className="num">Reviews</th>
                <th className="num" title="Bayesian score used for ranking">Score</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.id}>
                  <td className="rank">{r.rank}</td>
                  <td>
                    <Link to={`/universities/${r.id}`}>{r.name}</Link>
                    <div className="muted small">
                      {r.city}, {r.country}
                    </div>
                  </td>
                  <td className="num">{formatScore(r.average)}</td>
                  <td className="num">{r.reviewCount}</td>
                  <td className="num">
                    <strong>{r.score.toFixed(2)}</strong>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
