import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { errorMessage, getFollow, getUniversity, toggleFollow } from '../api.js';
import { useAuth } from '../AuthContext.jsx';
import RatingBars from '../components/RatingBars.jsx';
import { formatScore, reviewCountLabel, Stars } from '../components/StarRating.jsx';
import { Tabs } from '../components/Common.jsx';
import OverviewTab from './university/OverviewTab.jsx';
import PlacesTab from './university/PlacesTab.jsx';
import InternationalTab from './university/InternationalTab.jsx';
import QuestionsTab from './university/QuestionsTab.jsx';
import PhotosTab from './university/PhotosTab.jsx';

const TABS = [
  { key: 'overview', label: 'Overview' },
  { key: 'DORM', label: 'Dorms' },
  { key: 'DINING', label: 'Dining' },
  { key: 'COURSE', label: 'Courses' },
  { key: 'JOB', label: 'Campus jobs' },
  { key: 'CLUB', label: 'Clubs' },
  { key: 'international', label: 'International' },
  { key: 'questions', label: 'Q&A' },
  { key: 'photos', label: 'Photos' },
];

export default function UniversityPage() {
  const { id } = useParams();
  const { user } = useAuth();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const tab = searchParams.get('tab') || 'overview';

  const [university, setUniversity] = useState(null);
  const [follow, setFollow] = useState(null);
  const [error, setError] = useState('');

  const loadUniversity = useCallback(() => {
    getUniversity(id)
      .then(setUniversity)
      .catch((e) => setError(errorMessage(e)));
  }, [id]);

  useEffect(loadUniversity, [loadUniversity]);

  useEffect(() => {
    getFollow(id).then(setFollow).catch(() => setFollow(null));
  }, [id, user]);

  async function handleFollow() {
    if (!user) {
      navigate('/login', { state: { from: `/universities/${id}` } });
      return;
    }
    try {
      setFollow(await toggleFollow(id));
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  if (error && !university) return <div className="alert">{error}</div>;
  if (!university) return <div className="loading">Loading…</div>;

  const location = [university.city, university.state, university.country].filter(Boolean).join(', ');
  const isPlaceTab = ['DORM', 'DINING', 'COURSE', 'JOB', 'CLUB'].includes(tab);

  return (
    <>
      <section className="school-header">
        <div>
          <h1>{university.name}</h1>
          <p className="muted">
            {location}
            {university.website && (
              <>
                {' · '}
                <a href={university.website} target="_blank" rel="noreferrer">
                  Official website
                </a>
              </>
            )}
          </p>
        </div>
        <div className="header-actions">
          {follow && (
            <button type="button" className={`button button-secondary ${follow.following ? 'is-on' : ''}`} onClick={handleFollow}>
              {follow.following ? '✓ Following' : '+ Follow'} · {follow.followers}
            </button>
          )}
          <Link
            to={user ? `/universities/${id}/review` : '/login'}
            state={{ from: `/universities/${id}/review` }}
            className="button"
          >
            Write a review
          </Link>
        </div>
      </section>

      <section className="card school-summary">
        <div className="overall">
          <div className="overall-score">{formatScore(university.avgOverall)}</div>
          {university.reviewCount > 0 && <Stars value={university.avgOverall} />}
          <div className="muted small">{reviewCountLabel(university.reviewCount)}</div>
          {university.trendDelta > 0 && (
            <div className="trend-up small">▲ {university.trendDelta.toFixed(2)} this month</div>
          )}
        </div>
        <RatingBars university={university} />
      </section>

      {error && <div className="alert">{error}</div>}

      <div className="school-tabs">
        <Tabs
          tabs={TABS}
          active={tab}
          label="University sections"
          onChange={(key) => setSearchParams(key === 'overview' ? {} : { tab: key }, { replace: true })}
        />
      </div>

      {tab === 'overview' && <OverviewTab university={university} onChanged={loadUniversity} />}
      {isPlaceTab && <PlacesTab key={tab} university={university} type={tab} />}
      {tab === 'international' && <InternationalTab university={university} />}
      {tab === 'questions' && <QuestionsTab university={university} />}
      {tab === 'photos' && <PhotosTab university={university} />}
    </>
  );
}
