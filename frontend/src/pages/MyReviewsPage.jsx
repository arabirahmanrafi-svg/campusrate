import { useEffect, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { deleteReview, errorMessage, getMyReviews } from '../api.js';
import { useAuth } from '../AuthContext.jsx';
import ReviewCard from '../components/ReviewCard.jsx';

export default function MyReviewsPage() {
  const { user, checking } = useAuth();
  const [reviews, setReviews] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!user) return;
    getMyReviews()
      .then(setReviews)
      .catch((e) => setError(errorMessage(e)));
  }, [user]);

  if (checking) return <div className="loading">Loading…</div>;
  if (!user) return <Navigate to="/login" state={{ from: '/me/reviews' }} replace />;

  async function handleDelete(review) {
    if (!window.confirm('Delete your review? This cannot be undone.')) return;
    try {
      await deleteReview(review.id);
      setReviews((list) => list.filter((r) => r.id !== review.id));
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  return (
    <>
      <h1>My reviews</h1>
      {error && <div className="alert">{error}</div>}
      {reviews && reviews.length === 0 && (
        <div className="card empty">
          You haven't written any reviews yet. <Link to="/">Find your university</Link> and share your experience.
        </div>
      )}
      {reviews?.map((review) => (
        <ReviewCard key={review.id} review={review} showUniversity onDelete={handleDelete} />
      ))}
    </>
  );
}
