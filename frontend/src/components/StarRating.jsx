/** Shows a score like 4.3 with one decimal, or a dash when there's nothing yet. */
export function formatScore(value) {
  return value > 0 ? value.toFixed(1) : '–';
}

/** "1 review", "12 reviews" */
export function reviewCountLabel(count) {
  return `${count} ${count === 1 ? 'review' : 'reviews'}`;
}

/** Read-only stars, e.g. ★★★★☆ for 4. */
export function Stars({ value }) {
  const rounded = Math.round(value);
  return (
    <span className="stars" aria-label={`${value} out of 5`}>
      {'★'.repeat(rounded)}
      <span className="stars-empty">{'★'.repeat(5 - rounded)}</span>
    </span>
  );
}

/** Clickable 1–5 star picker for the review form. Works with the keyboard too (it's a radio group). */
export function StarInput({ label, hint, value, onChange, error }) {
  const name = label.toLowerCase().replace(/\s+/g, '-');
  return (
    <fieldset className={`star-input ${error ? 'has-error' : ''}`}>
      <legend>
        {label}
        {hint && <span className="muted small"> · {hint}</span>}
      </legend>
      <div className="star-input-row">
        {[1, 2, 3, 4, 5].map((n) => (
          <label key={n} className={n <= value ? 'on' : ''} title={`${n} star${n > 1 ? 's' : ''}`}>
            <input
              type="radio"
              name={name}
              value={n}
              checked={value === n}
              onChange={() => onChange(n)}
            />
            ★
          </label>
        ))}
        <span className="star-input-value muted small">{value ? `${value}/5` : 'Not rated'}</span>
      </div>
      {error && <div className="field-error">{error}</div>}
    </fieldset>
  );
}
