import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { compareUniversities, errorMessage, searchUniversities } from '../api.js';
import { RadarChart, SERIES_COLORS } from '../components/Charts.jsx';
import { formatScore } from '../components/StarRating.jsx';

const ROWS = [
  ['avgOverall', 'Overall'],
  ['avgAcademics', 'Academics'],
  ['avgCampusLife', 'Campus life'],
  ['avgHousing', 'Housing'],
  ['avgFood', 'Food'],
  ['avgSafety', 'Safety'],
  ['avgInternational', 'International'],
  ['reviewCount', 'Reviews'],
];

/** A small search box that returns the school you pick. */
function SchoolPicker({ onPick, exclude }) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState([]);

  useEffect(() => {
    if (query.trim().length < 2) {
      setResults([]);
      return;
    }
    const timer = setTimeout(() => {
      searchUniversities(query).then(setResults).catch(() => setResults([]));
    }, 250);
    return () => clearTimeout(timer);
  }, [query]);

  return (
    <div className="picker">
      <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Add a university…" aria-label="Add a university" />
      {results.length > 0 && (
        <ul className="search-results">
          {results
            .filter((u) => !exclude.includes(u.id))
            .map((u) => (
              <li
                key={u.id}
                onMouseDown={() => {
                  onPick(u.id);
                  setQuery('');
                  setResults([]);
                }}
              >
                <span className="search-name">{u.name}</span>
                <span className="muted small">{u.country}</span>
              </li>
            ))}
        </ul>
      )}
    </div>
  );
}

export default function ComparePage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const ids = (searchParams.get('ids') || '').split(',').filter(Boolean).map(Number);
  const [schools, setSchools] = useState([]);
  const [error, setError] = useState('');

  const idKey = ids.join(',');
  useEffect(() => {
    if (ids.length < 2) {
      setSchools([]);
      return;
    }
    compareUniversities(ids)
      .then(setSchools)
      .catch((e) => setError(errorMessage(e)));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [idKey]);

  const setIds = (next) => setSearchParams(next.length ? { ids: next.join(',') } : {});

  const showIntl = schools.some((s) => s.intlReviewCount > 0);
  const axes = ['Academics', 'Campus life', 'Housing', 'Food', 'Safety', ...(showIntl ? ['International'] : [])];

  return (
    <>
      <h1>Compare universities</h1>
      <p className="muted">Pick two or three schools to see them side by side.</p>

      <div className="compare-picked">
        {ids.map((id, i) => (
          <span key={id} className="chip" style={{ borderColor: SERIES_COLORS[i] }}>
            {schools.find((s) => s.id === id)?.name || `University ${id}`}
            <button type="button" className="link-button" onClick={() => setIds(ids.filter((x) => x !== id))} aria-label="Remove">
              ✕
            </button>
          </span>
        ))}
        {ids.length < 3 && <SchoolPicker exclude={ids} onPick={(id) => setIds([...ids, id])} />}
      </div>

      {error && <div className="alert">{error}</div>}

      {schools.length >= 2 && (
        <div className="compare-grid">
          <section className="card">
            <RadarChart
              axes={axes}
              series={schools.map((s) => ({
                name: s.name,
                values: [s.avgAcademics, s.avgCampusLife, s.avgHousing, s.avgFood, s.avgSafety, ...(showIntl ? [s.avgInternational] : [])],
              }))}
              ariaLabel="Category scores of the selected universities"
            />
            <div className="legend">
              {schools.map((s, i) => (
                <span key={s.id}>
                  <span className="legend-dot" style={{ background: SERIES_COLORS[i] }} /> {s.name}
                </span>
              ))}
            </div>
          </section>
          <section className="card table-wrap">
            <table className="rankings">
              <thead>
                <tr>
                  <th />
                  {schools.map((s) => (
                    <th key={s.id}>
                      <Link to={`/universities/${s.id}`}>{s.name}</Link>
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {ROWS.map(([key, label]) => {
                  const best = Math.max(...schools.map((s) => s[key]));
                  return (
                    <tr key={key}>
                      <td>{label}</td>
                      {schools.map((s) => (
                        <td key={s.id} className={`num ${s[key] === best && best > 0 && key !== 'reviewCount' ? 'best' : ''}`}>
                          {key === 'reviewCount' ? s[key] : formatScore(s[key])}
                        </td>
                      ))}
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </section>
        </div>
      )}
    </>
  );
}
