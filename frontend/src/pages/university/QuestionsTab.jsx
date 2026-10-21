import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { askQuestion, errorMessage, fieldErrors, getQuestions } from '../../api.js';
import { useAuth } from '../../AuthContext.jsx';
import { formatDate } from '../../components/Common.jsx';

export default function QuestionsTab({ university }) {
  const { user } = useAuth();
  const [questions, setQuestions] = useState(null);
  const [form, setForm] = useState({ title: '', body: '' });
  const [errors, setErrors] = useState({});
  const [error, setError] = useState('');

  useEffect(() => {
    getQuestions(university.id)
      .then(setQuestions)
      .catch((e) => setError(errorMessage(e)));
  }, [university.id]);

  async function handleAsk(e) {
    e.preventDefault();
    setErrors({});
    setError('');
    try {
      const created = await askQuestion(university.id, form);
      setQuestions((list) => [created, ...list]);
      setForm({ title: '', body: '' });
    } catch (err) {
      setErrors(fieldErrors(err));
      setError(errorMessage(err));
    }
  }

  return (
    <>
      {user ? (
        <form className="card form compact" onSubmit={handleAsk}>
          <strong>Ask current students</strong>
          <label className="field">
            <span className="small">Question</span>
            <input
              required
              minLength={10}
              maxLength={150}
              value={form.title}
              onChange={(e) => setForm({ ...form, title: e.target.value })}
              placeholder="e.g. How hard is it to find a campus job as an international student?"
            />
            {errors.title && <span className="field-error">{errors.title}</span>}
          </label>
          <label className="field">
            <span className="small">Details (optional)</span>
            <textarea rows={3} maxLength={2000} value={form.body} onChange={(e) => setForm({ ...form, body: e.target.value })} />
          </label>
          <button type="submit" className="button button-small">
            Post question
          </button>
        </form>
      ) : (
        <p className="muted">
          <Link to="/login">Log in</Link> to ask or answer questions.
        </p>
      )}

      {error && <div className="alert">{error}</div>}
      {questions && questions.length === 0 && <div className="card empty">No questions yet. Ask the first one!</div>}

      <div className="question-list">
        {questions?.map((q) => (
          <Link key={q.id} to={`/questions/${q.id}`} className="card question-item">
            <div>
              <strong>{q.title}</strong>
              <div className="muted small">
                Asked by {q.authorName} · {formatDate(q.createdAt)}
              </div>
            </div>
            <span className={`answer-count ${q.answerCount > 0 ? 'has-answers' : ''}`}>
              {q.answerCount} {q.answerCount === 1 ? 'answer' : 'answers'}
            </span>
          </Link>
        ))}
      </div>
    </>
  );
}
