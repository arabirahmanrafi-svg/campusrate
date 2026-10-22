import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { errorMessage, getValueForMoney } from '../api.js';
import { ScatterChart } from '../components/Charts.jsx';
import { formatMoney } from '../components/Common.jsx';
import usePageMeta from '../usePageMeta.js';

/** Yearly cost (from student cost reports) against average rating: which schools are worth the money? */
export default function WorthItPage() {
  usePageMeta({ title: 'Is it worth the money? University cost vs. happiness', description: 'See what students really pay to live near each university against how happy they are there.' });
  const navigate = useNavigate();
  const [points, setPoints] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    getValueForMoney()
      .then(setPoints)
      .catch((e) => setError(errorMessage(e)));
  }, []);

  const shortName = (name) => (name.length > 22 ? `${name.slice(0, 20)}…` : name);

  return (
    <>
      <h1>Worth the money?</h1>
      <p className="muted">
        Each dot is a university: how much students say a year costs (living costs plus tuition, from cost reports on the
        International tab) against its average rating. Up and to the left is great value.
      </p>
      {error && <div className="alert">{error}</div>}
      {points && points.length === 0 && (
        <div className="card empty">
          No data yet. A school appears here once it has reviews and at least one student has shared their costs.
        </div>
      )}
      {points && points.length > 0 && (
        <section className="card">
          <ScatterChart
            points={points.map((p) => ({ id: p.id, x: p.yearlyCost, y: p.avgOverall, label: shortName(p.name) }))}
            xLabel="Typical total cost per year (USD)"
            yLabel="Average rating"
            formatX={(v) => formatMoney(v)}
            onSelect={(p) => navigate(`/universities/${p.id}?tab=international`)}
            ariaLabel="Yearly cost versus average rating"
          />
        </section>
      )}
    </>
  );
}
