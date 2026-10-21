import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { errorMessage, fieldErrors, getInternational, reportCosts, suggestLink } from '../../api.js';
import { useAuth } from '../../AuthContext.jsx';
import { formatScore } from '../../components/StarRating.jsx';
import { formatMoney } from '../../components/Common.jsx';

const INTL_ROWS = [
  ['avgIntlSupport', 'International student office'],
  ['avgVisaHelp', 'Visa / I-20 help'],
  ['avgJobAvailability', 'On-campus job availability'],
  ['avgWelcoming', 'Welcoming community'],
];

const KIND_LABELS = { FOOD: 'Cultural food', ASSOCIATION: 'Student associations', OTHER: 'Other' };

function CostForm({ universityId, onSaved }) {
  const [form, setForm] = useState({ rent: '', groceries: '', transport: '', phone: '', tuitionPerYear: '' });
  const [errors, setErrors] = useState({});
  const [message, setMessage] = useState('');
  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value });

  async function handleSubmit(e) {
    e.preventDefault();
    setErrors({});
    setMessage('');
    const toNumber = (v) => (v === '' ? null : Number(v));
    try {
      const summary = await reportCosts(universityId, {
        rent: toNumber(form.rent),
        groceries: toNumber(form.groceries),
        transport: toNumber(form.transport),
        phone: toNumber(form.phone),
        tuitionPerYear: toNumber(form.tuitionPerYear),
      });
      setMessage('Thanks! Your numbers were added.');
      onSaved(summary);
    } catch (err) {
      setErrors(fieldErrors(err));
      setMessage(errorMessage(err));
    }
  }

  const fields = [
    ['rent', 'Rent per month'],
    ['groceries', 'Groceries per month'],
    ['transport', 'Transport per month'],
    ['phone', 'Phone per month'],
    ['tuitionPerYear', 'Tuition per year (optional)'],
  ];

  return (
    <form className="card form compact" onSubmit={handleSubmit}>
      <strong>Share what you pay (US dollars)</strong>
      <div className="cost-fields">
        {fields.map(([key, label]) => (
          <label key={key} className="field">
            <span className="small">{label}</span>
            <input type="number" min="0" value={form[key]} onChange={set(key)} required={key !== 'tuitionPerYear'} />
            {errors[key] && <span className="field-error">{errors[key]}</span>}
          </label>
        ))}
      </div>
      <button type="submit" className="button button-small">
        Add my costs
      </button>
      {message && <span className="muted small">{message}</span>}
    </form>
  );
}

function LinkForm({ universityId }) {
  const [form, setForm] = useState({ kind: 'FOOD', name: '', url: '', description: '' });
  const [message, setMessage] = useState('');
  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value });

  async function handleSubmit(e) {
    e.preventDefault();
    try {
      const link = await suggestLink(universityId, form);
      setMessage(link.status === 'APPROVED' ? 'Added.' : 'Thanks! It will appear after a moderator approves it.');
      setForm({ kind: 'FOOD', name: '', url: '', description: '' });
    } catch (err) {
      setMessage(errorMessage(err));
    }
  }

  return (
    <form className="card form compact" onSubmit={handleSubmit}>
      <strong>Suggest a place or group</strong>
      <div className="field-row">
        <label className="field">
          <span className="small">Type</span>
          <select value={form.kind} onChange={set('kind')}>
            <option value="FOOD">Cultural food spot</option>
            <option value="ASSOCIATION">Student association</option>
            <option value="OTHER">Other</option>
          </select>
        </label>
        <label className="field">
          <span className="small">Name</span>
          <input required maxLength={150} value={form.name} onChange={set('name')} />
        </label>
      </div>
      <label className="field">
        <span className="small">Website (optional)</span>
        <input type="url" maxLength={500} value={form.url} onChange={set('url')} placeholder="https://" />
      </label>
      <label className="field">
        <span className="small">Why it's useful (optional)</span>
        <input maxLength={300} value={form.description} onChange={set('description')} />
      </label>
      <button type="submit" className="button button-small">
        Suggest
      </button>
      {message && <span className="muted small">{message}</span>}
    </form>
  );
}

/** Everything an international student wants to know before choosing a school. */
export default function InternationalTab({ university }) {
  const { user } = useAuth();
  const [data, setData] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    getInternational(university.id)
      .then(setData)
      .catch((e) => setError(errorMessage(e)));
  }, [university.id]);

  if (error) return <div className="alert">{error}</div>;
  if (!data) return <div className="loading">Loading…</div>;

  const cost = data.cost;
  const linksByKind = data.links.reduce((groups, link) => {
    (groups[link.kind] ||= []).push(link);
    return groups;
  }, {});

  return (
    <div className="intl-grid">
      <section className="card">
        <h2>International student ratings</h2>
        {data.intlReviewCount === 0 ? (
          <p className="muted">
            No international students have rated {university.name} yet. International students can add these ratings when
            writing a review.
          </p>
        ) : (
          <>
            <p>
              <strong className="big-number">{formatScore(data.avgInternational)}</strong>
              <span className="muted"> from {data.intlReviewCount} international students</span>
            </p>
            <div className="rating-bars">
              {INTL_ROWS.map(([key, label]) => (
                <div className="rating-bar" key={key}>
                  <span className="rating-bar-label">{label}</span>
                  <span className="rating-bar-track">
                    <span className="rating-bar-fill" style={{ width: `${(data[key] / 5) * 100}%` }} />
                  </span>
                  <span className="rating-bar-value">{formatScore(data[key])}</span>
                </div>
              ))}
            </div>
          </>
        )}
        {data.countries.length > 0 && (
          <>
            <h3>Where reviewers come from</h3>
            <p className="chips">
              {data.countries.map((c) => (
                <span key={c.country} className="chip">
                  {c.country} · {c.count}
                </span>
              ))}
            </p>
            <p className="muted small">
              Only students who chose to show their home country are counted. Filter reviews by country on the Overview tab.
            </p>
          </>
        )}
      </section>

      <section className="card">
        <h2>Cost of living</h2>
        {cost.reports === 0 ? (
          <p className="muted">No one has shared their costs yet.</p>
        ) : (
          <>
            <table className="cost-table">
              <tbody>
                <tr><td>Rent</td><td>{formatMoney(cost.rent)}/month</td></tr>
                <tr><td>Groceries</td><td>{formatMoney(cost.groceries)}/month</td></tr>
                <tr><td>Transport</td><td>{formatMoney(cost.transport)}/month</td></tr>
                <tr><td>Phone</td><td>{formatMoney(cost.phone)}/month</td></tr>
                <tr className="total"><td>Living costs</td><td>{formatMoney(cost.monthlyTotal)}/month</td></tr>
                {cost.tuitionPerYear != null && <tr><td>Tuition</td><td>{formatMoney(cost.tuitionPerYear)}/year</td></tr>}
                <tr className="total"><td>Total per year</td><td>{formatMoney(cost.yearlyTotal)}</td></tr>
              </tbody>
            </table>
            <p className="muted small">
              Typical (median) amounts from {cost.reports} {cost.reports === 1 ? 'student' : 'students'}. Medians ignore a few
              unusual answers, unlike averages.
            </p>
          </>
        )}
        {user ? (
          <CostForm universityId={university.id} onSaved={(summary) => setData({ ...data, cost: summary })} />
        ) : (
          <p className="muted small">
            <Link to="/login">Log in</Link> to share your costs.
          </p>
        )}
      </section>

      <section className="card intl-wide">
        <h2>Community and food</h2>
        {data.links.length === 0 && <p className="muted">Nothing listed yet. Know a good spot or group?</p>}
        {Object.entries(linksByKind).map(([kind, links]) => (
          <div key={kind}>
            <h3>{KIND_LABELS[kind]}</h3>
            <ul className="link-list">
              {links.map((link) => (
                <li key={link.id}>
                  {link.url ? (
                    <a href={link.url} target="_blank" rel="noreferrer">
                      {link.name}
                    </a>
                  ) : (
                    <strong>{link.name}</strong>
                  )}
                  {link.description && <span className="muted"> · {link.description}</span>}
                </li>
              ))}
            </ul>
          </div>
        ))}
        {user && <LinkForm universityId={university.id} />}
      </section>
    </div>
  );
}
