import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { errorMessage, findFit, getCountries } from '../api.js';
import { formatMoney } from '../components/Common.jsx';
import usePageMeta from '../usePageMeta.js';

const QUESTIONS = [
  ['academics', 'Strong academics'],
  ['campusLife', 'Active campus life'],
  ['housing', 'Good housing'],
  ['food', 'Good food'],
  ['safety', 'Safety'],
  ['international', 'Support for international students'],
];

const IMPORTANCE = ['Not important', 'A little', 'Somewhat', 'Important', 'Very important', 'Must have'];

/** "Find my fit": a short quiz that ranks schools by what the student cares about. */
export default function FitPage() {
  usePageMeta({ title: 'Find the right university for you', description: 'Answer six questions and get universities ranked by what matters to you, within your budget.' });
  const [weights, setWeights] = useState({ academics: 3, campusLife: 3, housing: 3, food: 2, safety: 3, international: 0 });
  const [maxYearlyCost, setMaxYearlyCost] = useState('');
  const [country, setCountry] = useState('');
  const [countries, setCountries] = useState([]);
  const [results, setResults] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    getCountries().then(setCountries).catch(() => setCountries([]));
  }, []);

  async function handleSubmit(e) {
    e.preventDefault();
    setError('');
    try {
      setResults(
        await findFit({ ...weights, country: country || null, maxYearlyCost: maxYearlyCost ? Number(maxYearlyCost) : null }),
      );
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  return (
    <div className="fit-layout">
      <form className="card form" onSubmit={handleSubmit}>
        <h1>Find my fit</h1>
        <p className="muted">How much does each of these matter to you?</p>
        {QUESTIONS.map(([key, label]) => (
          <label key={key} className="field slider">
            <span>
              {label} <span className="muted small">· {IMPORTANCE[weights[key]]}</span>
            </span>
            <input
              type="range"
              min="0"
              max="5"
              value={weights[key]}
              onChange={(e) => setWeights({ ...weights, [key]: Number(e.target.value) })}
            />
          </label>
        ))}
        <div className="field-row">
          <label className="field">
            <span className="small">Country (optional)</span>
            <select value={country} onChange={(e) => setCountry(e.target.value)}>
              <option value="">Anywhere</option>
              {countries.map((c) => (
                <option key={c}>{c}</option>
              ))}
            </select>
          </label>
          <label className="field">
            <span className="small">Max total cost per year, USD (optional)</span>
            <input type="number" min="0" step="1000" value={maxYearlyCost} onChange={(e) => setMaxYearlyCost(e.target.value)} />
          </label>
        </div>
        <button type="submit" className="button">
          Show my matches
        </button>
      </form>

      <section>
        {error && <div className="alert">{error}</div>}
        {results && results.length === 0 && (
          <div className="card empty">No reviewed universities match yet. Try fewer filters.</div>
        )}
        {results?.map((r, i) => (
          <Link key={r.id} to={`/universities/${r.id}`} className="card fit-result">
            <span className="rank">#{i + 1}</span>
            <div>
              <strong>{r.name}</strong>
              <div className="muted small">
                {r.city}, {r.country} · {r.reviewCount} reviews
                {r.yearlyCost != null && ` · about ${formatMoney(r.yearlyCost)}/year`}
              </div>
              {r.strengths.length > 0 && <div className="small">Strong in: {r.strengths.join(', ')}</div>}
            </div>
            <span className="match">{r.matchPercent}%</span>
          </Link>
        ))}
        {results?.length > 0 && (
          <p className="muted small">
            Match = your importance weights × each school's ranking scores. Schools without cost data are included, shown without a
            cost.
          </p>
        )}
      </section>
    </div>
  );
}
