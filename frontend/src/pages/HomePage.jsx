import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import SearchBox from '../components/SearchBox.jsx';
import { formatScore } from '../components/StarRating.jsx';
import { getRankings, getTrending } from '../api.js';

const DISCOVER = [
  { to: '/fit', title: 'Find my fit', text: 'Answer six questions and get schools ranked for what matters to you.' },
  { to: '/compare', title: 'Compare schools', text: 'Put two or three universities side by side, category by category.' },
  { to: '/worth-it', title: 'Worth the money?', text: 'See what students really pay against how happy they are.' },
  { to: '/rankings', title: 'Rankings', text: 'Fair rankings that don’t let a handful of reviews win.' },
];

export default function HomePage() {
  const [topSchools, setTopSchools] = useState([]);
  const [trending, setTrending] = useState([]);

  useEffect(() => {
    getRankings('overall')
      .then((data) => setTopSchools(data.slice(0, 5)))
      .catch(() => setTopSchools([]));
    getTrending().then(setTrending).catch(() => setTrending([]));
  }, []);

  return (
    <>
      <section className="hero">
        <h1>What is it really like to study there?</h1>
        <p className="hero-sub">
          Honest reviews from students about academics, campus life, dorms, food, courses and support for international
          students.
        </p>
        <SearchBox autoFocus />
        <p className="muted small hero-hint">Try “Michigan”, “Toronto”, “Dhaka”, or even a typo like “Michgan”</p>
      </section>

      <section className="discover">
        {DISCOVER.map((d) => (
          <Link key={d.to} to={d.to} className="card discover-card">
            <h3>{d.title}</h3>
            <p className="muted small">{d.text}</p>
          </Link>
        ))}
      </section>

      <div className="home-lists">
        {topSchools.length > 0 && (
          <section>
            <div className="section-header">
              <h2>Top rated right now</h2>
              <Link to="/rankings">See full rankings →</Link>
            </div>
            <ol className="top-list">
              {topSchools.map((s) => (
                <li key={s.id} className="card top-item">
                  <span className="rank">#{s.rank}</span>
                  <Link to={`/universities/${s.id}`} className="top-name">
                    {s.name}
                  </Link>
                  <span className="muted small">
                    {s.city}, {s.country}
                  </span>
                  <span className="top-score">
                    {formatScore(s.average)} <span className="muted small">({s.reviewCount})</span>
                  </span>
                </li>
              ))}
            </ol>
          </section>
        )}

        {trending.length > 0 && (
          <section>
            <div className="section-header">
              <h2>Trending up</h2>
              <span className="muted small">Ratings rising over the last 30 days</span>
            </div>
            <ol className="top-list">
              {trending.map((s) => (
                <li key={s.id} className="card top-item">
                  <span className="rank trend-up">▲</span>
                  <Link to={`/universities/${s.id}`} className="top-name">
                    {s.name}
                  </Link>
                  <span className="muted small">
                    {s.city}, {s.country}
                  </span>
                  <span className="top-score trend-up">+{s.trendDelta.toFixed(2)}</span>
                </li>
              ))}
            </ol>
          </section>
        )}
      </div>
    </>
  );
}
