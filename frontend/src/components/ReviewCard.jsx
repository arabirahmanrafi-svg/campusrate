import { Link } from 'react-router-dom';
import { Stars } from './StarRating.jsx';
import { AuthorBadges, formatDate, PhotoGrid } from './Common.jsx';

const SCORE_LABELS = [
  ['academics', 'Academics'],
  ['campusLife', 'Campus life'],
  ['housing', 'Housing'],
  ['food', 'Food'],
  ['safety', 'Safety'],
];

const INTL_LABELS = [
  ['intlSupport', 'International office'],
  ['visaHelp', 'Visa / I-20 help'],
  ['jobAvailability', 'On-campus jobs'],
  ['welcoming', 'Welcoming community'],
];

const STATUS_NOTES = {
  FLAGGED: 'Waiting for a moderator to check it. Only you can see it for now.',
  REMOVED: 'Removed by a moderator.',
};

/**
 * One review. The parent passes callbacks for the buttons, so this component
 * only displays things and never calls the API itself.
 */
export default function ReviewCard({ review, showUniversity = false, onHelpful, onDelete }) {
  return (
    <article className="card review" id={`review-${review.id}`}>
      <header className="review-header">
        <div className="review-score">{review.overall.toFixed(1)}</div>
        <div className="review-meta">
          {showUniversity && review.universityName && (
            <Link to={`/universities/${review.universityId}`} className="review-university">
              {review.universityName}
            </Link>
          )}
          <div>
            <strong>{review.author.name}</strong>
            {review.major && <span className="muted"> · {review.major}</span>}
            {review.gradYear && <span className="muted"> · Class of {review.gradYear}</span>}
          </div>
          <AuthorBadges author={review.author} />
          <div className="muted small">{formatDate(review.createdAt)}</div>
        </div>
      </header>

      {STATUS_NOTES[review.status] && <div className="notice small">{STATUS_NOTES[review.status]}</div>}

      <p className="review-body">{review.body}</p>

      <dl className="review-scores">
        {SCORE_LABELS.map(([key, label]) => (
          <div key={key}>
            <dt>{label}</dt>
            <dd>
              <Stars value={review[key]} />
            </dd>
          </div>
        ))}
      </dl>

      {review.intlSupport != null && (
        <dl className="review-scores review-scores-intl">
          {INTL_LABELS.map(([key, label]) => (
            <div key={key}>
              <dt>{label}</dt>
              <dd>
                <Stars value={review[key]} />
              </dd>
            </div>
          ))}
        </dl>
      )}

      <PhotoGrid photos={review.photos} />

      <footer className="review-actions">
        {review.mine ? (
          <>
            <span className="muted small">{review.helpfulCount} found this helpful</span>
            {review.status !== 'REMOVED' && (
              <Link to={`/reviews/${review.id}/edit`} state={{ review }} className="link-button">
                Edit
              </Link>
            )}
            {onDelete && (
              <button type="button" className="link-button danger" onClick={() => onDelete(review)}>
                Delete
              </button>
            )}
          </>
        ) : (
          onHelpful && (
            <button
              type="button"
              className={`chip ${review.votedByMe ? 'chip-on' : ''}`}
              onClick={() => onHelpful(review)}
              aria-pressed={review.votedByMe}
            >
              👍 Helpful · {review.helpfulCount}
            </button>
          )
        )}
      </footer>
    </article>
  );
}
