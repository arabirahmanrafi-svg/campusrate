import { useEffect, useState } from 'react';
import { Navigate, useLocation, useNavigate, useParams } from 'react-router-dom';
import {
  createReview,
  errorMessage,
  fieldErrors,
  getMyReviews,
  getUniversity,
  updateReview,
  uploadPhoto,
} from '../api.js';
import { useAuth } from '../AuthContext.jsx';
import { StarInput } from '../components/StarRating.jsx';
import { Honeypot } from '../components/Common.jsx';
import usePageMeta from '../usePageMeta.js';

const RATINGS = [
  { field: 'academics', label: 'Academics', hint: 'teaching, classes, professors' },
  { field: 'campusLife', label: 'Campus life', hint: 'clubs, events, social life' },
  { field: 'housing', label: 'Housing', hint: 'dorms and nearby rentals' },
  { field: 'food', label: 'Food', hint: 'dining halls and options' },
  { field: 'safety', label: 'Safety', hint: 'on and around campus' },
];

const INTL_RATINGS = [
  { field: 'intlSupport', label: 'International student office', hint: 'helpful, easy to reach' },
  { field: 'visaHelp', label: 'Visa / I-20 help', hint: 'fast, clear answers' },
  { field: 'jobAvailability', label: 'On-campus jobs', hint: 'easy to find as an international student' },
  { field: 'welcoming', label: 'Welcoming community', hint: 'do you feel at home?' },
];

const EMPTY = {
  academics: 0, campusLife: 0, housing: 0, food: 0, safety: 0,
  intlSupport: 0, visaHelp: 0, jobAvailability: 0, welcoming: 0,
  body: '', major: '', gradYear: '',
};
const MIN_LENGTH = 30;
const MAX_PHOTOS = 3;

/** Used for both writing a new review (/universities/:id/review) and editing one (/reviews/:reviewId/edit). */
export default function ReviewFormPage() {
  usePageMeta({ title: 'Write a review', noindex: true });
  const { id, reviewId } = useParams();
  const isEdit = Boolean(reviewId);
  const location = useLocation();
  const navigate = useNavigate();
  const { user, checking } = useAuth();

  const [universityId, setUniversityId] = useState(id ? Number(id) : null);
  const [universityName, setUniversityName] = useState('');
  const [form, setForm] = useState(EMPTY);
  const [withIntl, setWithIntl] = useState(false);
  const [photos, setPhotos] = useState([]);
  const [website, setWebsite] = useState('');
  const [errors, setErrors] = useState({});
  const [message, setMessage] = useState('');
  const [saving, setSaving] = useState(false);

  // International students see the extra ratings switched on by default.
  useEffect(() => {
    if (user?.international && !isEdit) setWithIntl(true);
  }, [user, isEdit]);

  // Load what the form needs: the school name (new review) or the existing review (edit).
  useEffect(() => {
    if (!user) return;
    if (isEdit) {
      const fromState = location.state?.review;
      const load = fromState
        ? Promise.resolve(fromState)
        : getMyReviews().then((list) => list.find((r) => String(r.id) === reviewId));
      load
        .then((review) => {
          if (!review) {
            setMessage('Review not found.');
            return;
          }
          setUniversityId(review.universityId);
          setWithIntl(review.intlSupport != null);
          setForm({
            academics: review.academics,
            campusLife: review.campusLife,
            housing: review.housing,
            food: review.food,
            safety: review.safety,
            intlSupport: review.intlSupport || 0,
            visaHelp: review.visaHelp || 0,
            jobAvailability: review.jobAvailability || 0,
            welcoming: review.welcoming || 0,
            body: review.body,
            major: review.major || '',
            gradYear: review.gradYear || '',
          });
          getUniversity(review.universityId).then((u) => setUniversityName(u.name));
        })
        .catch((e) => setMessage(errorMessage(e)));
    } else {
      getUniversity(id)
        .then((u) => setUniversityName(u.name))
        .catch((e) => setMessage(errorMessage(e)));
    }
  }, [id, reviewId, isEdit, user, location.state]);

  if (checking) return <div className="loading">Loading…</div>;
  if (!user) return <Navigate to="/login" state={{ from: location.pathname }} replace />;

  const set = (field, value) => setForm((f) => ({ ...f, [field]: value }));

  // Check in the browser first for instant feedback. The backend checks again,
  // because anyone can call the API directly and skip this code.
  function validate() {
    const found = {};
    const required = withIntl ? [...RATINGS, ...INTL_RATINGS] : RATINGS;
    required.forEach(({ field }) => {
      if (!form[field]) found[field] = 'Please choose 1 to 5 stars.';
    });
    if (form.body.trim().length < MIN_LENGTH) found.body = `Please write at least ${MIN_LENGTH} characters.`;
    if (photos.some((p) => p.size > 5 * 1024 * 1024)) found.photos = 'Each photo must be 5 MB or smaller.';
    return found;
  }

  async function handleSubmit(e) {
    e.preventDefault();
    const found = validate();
    setErrors(found);
    if (Object.keys(found).length) return;

    setSaving(true);
    setMessage('');
    const intl = (field) => (withIntl ? form[field] : null);
    const payload = {
      ...form,
      universityId,
      intlSupport: intl('intlSupport'),
      visaHelp: intl('visaHelp'),
      jobAvailability: intl('jobAvailability'),
      welcoming: intl('welcoming'),
      body: form.body.trim(),
      gradYear: form.gradYear ? Number(form.gradYear) : null,
      website,
    };
    try {
      const saved = isEdit ? await updateReview(reviewId, payload) : await createReview(payload);
      // Photos are uploaded after the review exists, so they can be attached to it.
      for (const photo of photos) {
        await uploadPhoto(universityId, photo, { reviewId: saved.id });
      }
      navigate(`/universities/${universityId}`);
    } catch (err) {
      setErrors(fieldErrors(err));
      setMessage(errorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  const remaining = MIN_LENGTH - form.body.trim().length;

  return (
    <div className="narrow">
      <h1>{isEdit ? 'Edit your review' : 'Write a review'}</h1>
      {universityName && <p className="muted">{universityName}</p>}

      {message && <div className="alert">{message}</div>}

      <form className="card form" onSubmit={handleSubmit} noValidate>
        {RATINGS.map(({ field, label, hint }) => (
          <StarInput
            key={field}
            label={label}
            hint={hint}
            value={form[field]}
            onChange={(v) => set(field, v)}
            error={errors[field]}
          />
        ))}

        <label className="checkbox">
          <input type="checkbox" checked={withIntl} onChange={(e) => setWithIntl(e.target.checked)} />
          I'm an international student: add ratings for international support
        </label>
        {withIntl && (
          <div className="intl-section">
            {INTL_RATINGS.map(({ field, label, hint }) => (
              <StarInput
                key={field}
                label={label}
                hint={hint}
                value={form[field]}
                onChange={(v) => set(field, v)}
                error={errors[field]}
              />
            ))}
          </div>
        )}

        <label className="field">
          <span>Your review</span>
          <textarea
            rows={7}
            maxLength={2000}
            value={form.body}
            onChange={(e) => set('body', e.target.value)}
            placeholder="What should a future student know? What's great, and what could be better?"
            aria-invalid={Boolean(errors.body)}
          />
          <span className="muted small">
            {remaining > 0 ? `${remaining} more characters needed` : `${form.body.length}/2000`}
          </span>
          {errors.body && <span className="field-error">{errors.body}</span>}
        </label>

        <div className="field-row">
          <label className="field">
            <span>
              Major <span className="muted small">(optional)</span>
            </span>
            <input value={form.major} maxLength={100} onChange={(e) => set('major', e.target.value)} />
          </label>
          <label className="field">
            <span>
              Graduation year <span className="muted small">(optional)</span>
            </span>
            <input
              type="number"
              min={1950}
              max={2040}
              value={form.gradYear}
              onChange={(e) => set('gradYear', e.target.value)}
            />
            {errors.gradYear && <span className="field-error">{errors.gradYear}</span>}
          </label>
        </div>

        <label className="field">
          <span>
            Photos <span className="muted small">(optional, up to {MAX_PHOTOS}, shown after a moderator approves them)</span>
          </span>
          <input
            type="file"
            accept="image/jpeg,image/png,image/webp"
            multiple
            onChange={(e) => setPhotos(Array.from(e.target.files).slice(0, MAX_PHOTOS))}
          />
          {errors.photos && <span className="field-error">{errors.photos}</span>}
        </label>

        <Honeypot value={website} onChange={setWebsite} />

        <div className="form-actions">
          <button type="button" className="button button-secondary" onClick={() => navigate(-1)}>
            Cancel
          </button>
          <button type="submit" className="button" disabled={saving}>
            {saving ? 'Saving…' : isEdit ? 'Save changes' : 'Post review'}
          </button>
        </div>
      </form>
    </div>
  );
}
