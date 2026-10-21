import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { searchUniversities } from '../api.js';
import { formatScore, reviewCountLabel } from './StarRating.jsx';

/**
 * University search with autocomplete.
 * Waits 250 ms after the user stops typing before calling the API ("debouncing"),
 * so typing "michigan" sends one request instead of eight.
 */
export default function SearchBox({ autoFocus = false }) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState([]);
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);
  const navigate = useNavigate();

  useEffect(() => {
    if (query.trim().length < 2) {
      setResults([]);
      return;
    }
    let cancelled = false; // ignore answers that arrive after the user typed something newer
    const timer = setTimeout(() => {
      searchUniversities(query)
        .then((data) => {
          if (!cancelled) {
            setResults(data);
            setActive(-1);
          }
        })
        .catch(() => !cancelled && setResults([]));
    }, 250);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [query]);

  function choose(university) {
    setOpen(false);
    setQuery('');
    navigate(`/universities/${university.id}`);
  }

  // Arrow keys move through results, Enter opens one.
  function handleKeyDown(e) {
    if (!results.length) return;
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setActive((i) => Math.min(i + 1, results.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActive((i) => Math.max(i - 1, 0));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      choose(results[Math.max(active, 0)]);
    } else if (e.key === 'Escape') {
      setOpen(false);
    }
  }

  const showResults = open && query.trim().length >= 2;

  return (
    <div className="search">
      <input
        type="search"
        className="search-input"
        placeholder="Search for your university or city…"
        value={query}
        autoFocus={autoFocus}
        onChange={(e) => {
          setQuery(e.target.value);
          setOpen(true);
        }}
        onFocus={() => setOpen(true)}
        onBlur={() => setTimeout(() => setOpen(false), 150)}
        onKeyDown={handleKeyDown}
        aria-label="Search universities"
        role="combobox"
        aria-expanded={showResults}
        aria-controls="search-results"
      />
      {showResults && (
        <ul className="search-results" id="search-results" role="listbox">
          {results.length === 0 && <li className="search-empty">No universities found</li>}
          {results.map((u, i) => (
            <li
              key={u.id}
              role="option"
              aria-selected={i === active}
              className={i === active ? 'active' : ''}
              onMouseDown={() => choose(u)}
            >
              <div>
                <div className="search-name">{u.name}</div>
                <div className="muted small">
                  {u.city}
                  {u.state ? `, ${u.state}` : ''} · {u.country}
                </div>
              </div>
              <div className="search-score">
                {u.reviewCount > 0 ? (
                  <>
                    <strong>{formatScore(u.avgOverall)}</strong>
                    <span className="muted small"> · {reviewCountLabel(u.reviewCount)}</span>
                  </>
                ) : (
                  <span className="muted small">No reviews yet</span>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
