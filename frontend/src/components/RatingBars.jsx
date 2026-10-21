import { formatScore } from './StarRating.jsx';

export const CATEGORIES = [
  { key: 'academics', field: 'avgAcademics', label: 'Academics' },
  { key: 'campus_life', field: 'avgCampusLife', label: 'Campus life' },
  { key: 'housing', field: 'avgHousing', label: 'Housing' },
  { key: 'food', field: 'avgFood', label: 'Food' },
  { key: 'safety', field: 'avgSafety', label: 'Safety' },
];

/** A horizontal bar per category, filled in proportion to the 1–5 score. */
export default function RatingBars({ university }) {
  return (
    <div className="rating-bars">
      {CATEGORIES.map((c) => {
        const value = university[c.field];
        return (
          <div className="rating-bar" key={c.key}>
            <span className="rating-bar-label">{c.label}</span>
            <span className="rating-bar-track">
              <span className="rating-bar-fill" style={{ width: `${(value / 5) * 100}%` }} />
            </span>
            <span className="rating-bar-value">{formatScore(value)}</span>
          </div>
        );
      })}
    </div>
  );
}
