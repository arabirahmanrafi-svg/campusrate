import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  askReviews,
  deleteReview,
  errorMessage,
  getInsights,
  getInternational,
  getReviews,
  toggleHelpful,
} from '../../api.js';
import { useAuth } from '../../AuthContext.jsx';
import ReviewCard from '../../components/ReviewCard.jsx';
import { LineChart } from '../../components/Charts.jsx';
import { CampusMap } from '../../components/Common.jsx';

const SORTS = [
  { value: 'newest', label: 'Newest' },
  { value: 'helpful', label: 'Most helpful' },
  { value: 'highest', label: 'Highest rated' },
  { value: 'lowest', label: 'Lowest rated' },
];

/** Scrolls to a review on the page when a quote is clicked. */
function scrollToReview(id) {
  document.getElementById(`review-${id}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' });
}

function Insights({ universityId }) {
  const [insights, setInsights] = useState(null);

  useEffect(() => {
    getInsights(universityId).then(setInsights).catch(() => setInsights(null));
  }, [universityId]);

  if (!insights) return null;

  return (
    <section className="card insights">
      <h2>At a glance</h2>
      <p>{insights.summary}</p>
      {(insights.praise.length > 0 || insights.concerns.length > 0) && (
        <div className="quotes">
          <div>
            <h3>What students like</h3>
            {insights.praise.map((q) => (
              <blockquote key={q.reviewId} onClick={() => scrollToReview(q.reviewId)} className="quote quote-good">
                “{q.sentence}”
              </blockquote>
            ))}
          </div>
          <div>
            <h3>Common complaints</h3>
            {insights.concerns.length === 0 && <p className="muted small">No common complaints yet.</p>}
            {insights.concerns.map((q) => (
              <blockquote key={q.reviewId} onClick={() => scrollToReview(q.reviewId)} className="quote quote-bad">
                “{q.sentence}”
              </blockquote>
            ))}
          </div>
        </div>
      )}
      {insights.trend.length >= 2 && (
        <>
          <h3>Mood of reviews by semester</h3>
          <p className="muted small">From −1 (very negative) to +1 (very positive), worked out from the review text.</p>
          <LineChart
            points={insights.trend.map((p) => ({ label: p.semester, value: p.avgSentiment, note: `${p.count} reviews` }))}
            min={-1}
            max={1}
            zeroLine
            ariaLabel="Average review sentiment per semester"
          />
        </>
      )}
      <p className="muted small">Quotes are taken word for word from student reviews.</p>
    </section>
  );
}

function AskTheReviews({ universityId }) {
  const [question, setQuestion] = useState('');
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');

  async function handleAsk(e) {
    e.preventDefault();
    setError('');
    try {
      setResult(await askReviews(universityId, question));
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  return (
    <section className="card ask">
      <h2>Ask the reviews</h2>
      <form onSubmit={handleAsk} className="inline-form">
        <input
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          placeholder="e.g. Is parking hard? Are there halal food options?"
          maxLength={200}
        />
        <button type="submit" className="button button-small" disabled={question.trim().length < 3}>
          Ask
        </button>
      </form>
      {error && <div className="alert">{error}</div>}
      {result && (
        <div className="ask-result">
          <p>{result.answer}</p>
          {result.citations.map((c, i) => (
            <blockquote key={`${c.reviewId}-${i}`} className="quote" onClick={() => scrollToReview(c.reviewId)}>
              “{c.sentence}”<span className="muted small"> — {c.authorName}</span>
            </blockquote>
          ))}
        </div>
      )}
    </section>
  );
}

export default function OverviewTab({ university, onChanged }) {
  const { user } = useAuth();
  const navigate = useNavigate();
  const id = university.id;

  const [reviews, setReviews] = useState(null);
  const [sort, setSort] = useState('newest');
  const [page, setPage] = useState(0);
  const [internationalOnly, setInternationalOnly] = useState(false);
  const [country, setCountry] = useState('');
  const [countries, setCountries] = useState([]);
  const [error, setError] = useState('');

  useEffect(() => {
    getInternational(id)
      .then((data) => setCountries(data.countries))
      .catch(() => setCountries([]));
  }, [id]);

  const loadReviews = useCallback(() => {
    getReviews(id, { sort, page, international: internationalOnly || undefined, country })
      .then(setReviews)
      .catch((e) => setError(errorMessage(e)));
    // user is a dependency so "mine" / "votedByMe" refresh after logging in or out
  }, [id, sort, page, internationalOnly, country, user]);

  useEffect(loadReviews, [loadReviews]);

  async function handleHelpful(review) {
    if (!user) {
      navigate('/login', { state: { from: `/universities/${id}` } });
      return;
    }
    try {
      const result = await toggleHelpful(review.id);
      // Update just this review in place instead of reloading the whole list.
      setReviews((prev) => ({
        ...prev,
        content: prev.content.map((r) =>
          r.id === review.id ? { ...r, votedByMe: result.voted, helpfulCount: result.helpfulCount } : r,
        ),
      }));
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  async function handleDelete(review) {
    if (!window.confirm('Delete your review? This cannot be undone.')) return;
    try {
      await deleteReview(review.id);
      onChanged();
      loadReviews();
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  const filtersOn = internationalOnly || country;

  return (
    <>
      <div className="overview-grid">
        <Insights universityId={id} />
        <div className="overview-side">
          <AskTheReviews universityId={id} />
          <CampusMap latitude={university.latitude} longitude={university.longitude} name={university.name} />
        </div>
      </div>

      {error && <div className="alert">{error}</div>}

      <section>
        <div className="section-header">
          <h2>Student reviews</h2>
          <div className="filters-inline">
            <label className="checkbox small">
              <input
                type="checkbox"
                checked={internationalOnly}
                onChange={(e) => {
                  setInternationalOnly(e.target.checked);
                  setPage(0);
                }}
              />
              International students only
            </label>
            {countries.length > 0 && (
              <label className="sort">
                From{' '}
                <select
                  value={country}
                  onChange={(e) => {
                    setCountry(e.target.value);
                    setPage(0);
                  }}
                >
                  <option value="">Any country</option>
                  {countries.map((c) => (
                    <option key={c.country} value={c.country}>
                      {c.country} ({c.count})
                    </option>
                  ))}
                </select>
              </label>
            )}
            <label className="sort">
              Sort by{' '}
              <select
                value={sort}
                onChange={(e) => {
                  setSort(e.target.value);
                  setPage(0);
                }}
              >
                {SORTS.map((s) => (
                  <option key={s.value} value={s.value}>
                    {s.label}
                  </option>
                ))}
              </select>
            </label>
          </div>
        </div>

        {reviews && reviews.totalElements === 0 && (
          <div className="card empty">
            <p>
              {filtersOn
                ? 'No reviews match these filters yet.'
                : `No reviews yet. Be the first student to review ${university.name}.`}
            </p>
          </div>
        )}

        {reviews?.content.map((review) => (
          <ReviewCard key={review.id} review={review} onHelpful={handleHelpful} onDelete={handleDelete} />
        ))}

        {reviews?.totalPages > 1 && (
          <nav className="pagination" aria-label="Review pages">
            <button type="button" className="button button-secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>
              ← Previous
            </button>
            <span className="muted">
              Page {page + 1} of {reviews.totalPages}
            </span>
            <button
              type="button"
              className="button button-secondary"
              disabled={page + 1 >= reviews.totalPages}
              onClick={() => setPage(page + 1)}
            >
              Next →
            </button>
          </nav>
        )}
      </section>
    </>
  );
}
