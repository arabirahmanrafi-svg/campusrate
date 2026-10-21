import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { answerQuestion, errorMessage, getQuestion, voteAnswer } from '../api.js';
import { useAuth } from '../AuthContext.jsx';
import { formatDate, STATUS_LABELS } from '../components/Common.jsx';

export default function QuestionPage() {
  const { id } = useParams();
  const { user } = useAuth();
  const navigate = useNavigate();
  const [detail, setDetail] = useState(null);
  const [answer, setAnswer] = useState('');
  const [error, setError] = useState('');

  function load() {
    getQuestion(id)
      .then(setDetail)
      .catch((e) => setError(errorMessage(e)));
  }

  useEffect(load, [id, user]);

  async function handleAnswer(e) {
    e.preventDefault();
    setError('');
    try {
      await answerQuestion(id, { body: answer });
      setAnswer('');
      load();
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  async function handleVote(a) {
    if (!user) {
      navigate('/login', { state: { from: `/questions/${id}` } });
      return;
    }
    try {
      const result = await voteAnswer(a.id);
      setDetail((d) => ({
        ...d,
        answers: d.answers.map((x) => (x.id === a.id ? { ...x, votes: result.votes, votedByMe: result.voted } : x)),
      }));
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  if (error && !detail) return <div className="alert">{error}</div>;
  if (!detail) return <div className="loading">Loading…</div>;

  const { question } = detail;

  return (
    <div className="narrow wide-narrow">
      <p className="breadcrumb">
        <Link to={`/universities/${question.universityId}?tab=questions`}>{detail.universityName} · Q&A</Link>
      </p>
      <h1>{question.title}</h1>
      <p className="muted small">
        Asked by {question.authorName} · {formatDate(question.createdAt)}
      </p>
      {question.body && <p className="review-body">{question.body}</p>}

      <h2 className="section-title">
        {detail.answers.length} {detail.answers.length === 1 ? 'answer' : 'answers'}
      </h2>
      {detail.answers.map((a) => (
        <article key={a.id} className="card answer">
          <button
            type="button"
            className={`vote ${a.votedByMe ? 'vote-on' : ''}`}
            onClick={() => handleVote(a)}
            disabled={a.mine}
            aria-pressed={a.votedByMe}
            title={a.mine ? "You can't vote on your own answer" : 'This answer was helpful'}
          >
            ▲<span>{a.votes}</span>
          </button>
          <div>
            <p className="review-body">{a.body}</p>
            <div className="muted small">
              {a.authorName} · {STATUS_LABELS[a.authorStatus] || 'Student'}
              {a.authorReputation > 0 && ` · ★ ${a.authorReputation}`} · {formatDate(a.createdAt)}
            </div>
          </div>
        </article>
      ))}

      {error && <div className="alert">{error}</div>}

      {user ? (
        <form className="card form" onSubmit={handleAnswer}>
          <label className="field">
            <span>Your answer</span>
            <textarea rows={4} minLength={5} maxLength={2000} required value={answer} onChange={(e) => setAnswer(e.target.value)} />
          </label>
          <div className="form-actions">
            <button type="submit" className="button">
              Post answer
            </button>
          </div>
        </form>
      ) : (
        <p className="muted">
          <Link to="/login" state={{ from: `/questions/${id}` }}>
            Log in
          </Link>{' '}
          to answer.
        </p>
      )}
    </div>
  );
}
