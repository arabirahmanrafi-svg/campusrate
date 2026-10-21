/*
 * Small charts drawn with plain SVG, so the project needs no chart library.
 * Each chart takes simple arrays and works out its own scale.
 */

const INK = '#5f6b7a';
const GRID = '#e3dfd6';
export const SERIES_COLORS = ['#1f3a5f', '#d98c1f', '#2f8f6b'];

/** Maps a value from [domainMin, domainMax] onto [rangeMin, rangeMax]. */
function scale(value, domainMin, domainMax, rangeMin, rangeMax) {
  if (domainMax === domainMin) return (rangeMin + rangeMax) / 2;
  return rangeMin + ((value - domainMin) / (domainMax - domainMin)) * (rangeMax - rangeMin);
}

/** Line over time, e.g. sentiment per semester. points: [{ label, value, note }] */
export function LineChart({ points, min, max, zeroLine = false, format = (v) => v.toFixed(2), ariaLabel }) {
  const width = 640;
  const height = 220;
  const pad = { top: 16, right: 40, bottom: 36, left: 44 };
  const lo = min ?? Math.min(...points.map((p) => p.value));
  const hi = max ?? Math.max(...points.map((p) => p.value));
  const x = (i) => scale(i, 0, Math.max(points.length - 1, 1), pad.left, width - pad.right);
  const y = (v) => scale(v, lo, hi, height - pad.bottom, pad.top);
  const ticks = [lo, (lo + hi) / 2, hi];
  const path = points.map((p, i) => `${i === 0 ? 'M' : 'L'}${x(i)},${y(p.value)}`).join(' ');

  return (
    <svg viewBox={`0 0 ${width} ${height}`} className="chart" role="img" aria-label={ariaLabel}>
      {ticks.map((t) => (
        <g key={t}>
          <line x1={pad.left} x2={width - pad.right} y1={y(t)} y2={y(t)} stroke={GRID} />
          <text x={pad.left - 8} y={y(t) + 4} textAnchor="end" fontSize="11" fill={INK}>
            {format(t)}
          </text>
        </g>
      ))}
      {zeroLine && lo < 0 && hi > 0 && (
        <line x1={pad.left} x2={width - pad.right} y1={y(0)} y2={y(0)} stroke={INK} strokeDasharray="4 4" />
      )}
      <path d={path} fill="none" stroke={SERIES_COLORS[0]} strokeWidth="2.5" />
      {points.map((p, i) => (
        <g key={p.label}>
          <circle cx={x(i)} cy={y(p.value)} r="5" fill={SERIES_COLORS[0]}>
            <title>{`${p.label}: ${format(p.value)}${p.note ? ` (${p.note})` : ''}`}</title>
          </circle>
          <text x={x(i)} y={height - 12} textAnchor="middle" fontSize="11" fill={INK}>
            {p.label}
          </text>
        </g>
      ))}
    </svg>
  );
}

/** Vertical bars, e.g. reviews per day. data: [{ label, value }] */
export function BarChart({ data, ariaLabel, labelEvery = 5 }) {
  const width = 640;
  const height = 200;
  const pad = { top: 12, right: 12, bottom: 32, left: 36 };
  const max = Math.max(1, ...data.map((d) => d.value));
  const slot = (width - pad.left - pad.right) / data.length;
  const y = (v) => scale(v, 0, max, height - pad.bottom, pad.top);

  return (
    <svg viewBox={`0 0 ${width} ${height}`} className="chart" role="img" aria-label={ariaLabel}>
      {[0, max].map((t) => (
        <g key={t}>
          <line x1={pad.left} x2={width - pad.right} y1={y(t)} y2={y(t)} stroke={GRID} />
          <text x={pad.left - 6} y={y(t) + 4} textAnchor="end" fontSize="11" fill={INK}>
            {t}
          </text>
        </g>
      ))}
      {data.map((d, i) => (
        <g key={d.label}>
          <rect
            x={pad.left + i * slot + slot * 0.15}
            y={y(d.value)}
            width={slot * 0.7}
            height={height - pad.bottom - y(d.value)}
            rx="2"
            fill={SERIES_COLORS[0]}
          >
            <title>{`${d.label}: ${d.value}`}</title>
          </rect>
          {i % labelEvery === 0 && (
            <text x={pad.left + i * slot + slot / 2} y={height - 12} textAnchor="middle" fontSize="10" fill={INK}>
              {d.label.slice(5)}
            </text>
          )}
        </g>
      ))}
    </svg>
  );
}

/** Radar ("spider") chart comparing schools across categories, all on a 0–5 scale. */
export function RadarChart({ axes, series, ariaLabel }) {
  const size = 360;
  const center = size / 2;
  const radius = 120;
  const angle = (i) => (Math.PI * 2 * i) / axes.length - Math.PI / 2;
  const point = (i, value) => [center + Math.cos(angle(i)) * radius * (value / 5), center + Math.sin(angle(i)) * radius * (value / 5)];

  return (
    <svg viewBox={`0 0 ${size} ${size}`} className="chart radar" role="img" aria-label={ariaLabel}>
      {[1, 2, 3, 4, 5].map((level) => (
        <polygon
          key={level}
          points={axes.map((_, i) => point(i, level).join(',')).join(' ')}
          fill="none"
          stroke={GRID}
        />
      ))}
      {axes.map((label, i) => {
        const [lx, ly] = point(i, 6.1);
        return (
          <g key={label}>
            <line x1={center} y1={center} x2={point(i, 5)[0]} y2={point(i, 5)[1]} stroke={GRID} />
            <text x={lx} y={ly + 4} textAnchor="middle" fontSize="12" fill={INK}>
              {label}
            </text>
          </g>
        );
      })}
      {series.map((s, si) => (
        <polygon
          key={s.name}
          points={s.values.map((v, i) => point(i, v).join(',')).join(' ')}
          fill={SERIES_COLORS[si]}
          fillOpacity="0.15"
          stroke={SERIES_COLORS[si]}
          strokeWidth="2"
        >
          <title>{s.name}</title>
        </polygon>
      ))}
    </svg>
  );
}

/** Dots on two axes, e.g. yearly cost vs. rating. points: [{ id, x, y, label }] */
export function ScatterChart({ points, xLabel, yLabel, formatX = (v) => v, ariaLabel, onSelect }) {
  const width = 640;
  const height = 360;
  const pad = { top: 16, right: 24, bottom: 52, left: 52 };
  const xs = points.map((p) => p.x);
  const xMin = Math.min(...xs) * 0.9;
  const xMax = Math.max(...xs) * 1.1;
  const x = (v) => scale(v, xMin, xMax, pad.left, width - pad.right);
  const y = (v) => scale(v, 1, 5, height - pad.bottom, pad.top);

  return (
    <svg viewBox={`0 0 ${width} ${height}`} className="chart" role="img" aria-label={ariaLabel}>
      {[1, 2, 3, 4, 5].map((t) => (
        <g key={t}>
          <line x1={pad.left} x2={width - pad.right} y1={y(t)} y2={y(t)} stroke={GRID} />
          <text x={pad.left - 8} y={y(t) + 4} textAnchor="end" fontSize="11" fill={INK}>
            {t}
          </text>
        </g>
      ))}
      {[xMin, (xMin + xMax) / 2, xMax].map((t) => (
        <text key={t} x={x(t)} y={height - 30} textAnchor="middle" fontSize="11" fill={INK}>
          {formatX(t)}
        </text>
      ))}
      <text x={(width + pad.left) / 2} y={height - 8} textAnchor="middle" fontSize="12" fill={INK}>
        {xLabel}
      </text>
      <text x={14} y={height / 2} textAnchor="middle" fontSize="12" fill={INK} transform={`rotate(-90 14 ${height / 2})`}>
        {yLabel}
      </text>
      {points.map((p) => (
        <g key={p.id} className="scatter-dot" onClick={() => onSelect?.(p)} style={{ cursor: onSelect ? 'pointer' : 'default' }}>
          <circle cx={x(p.x)} cy={y(p.y)} r="7" fill={SERIES_COLORS[0]} fillOpacity="0.8" />
          {/* Labels on the right half go to the left of the dot, so they never run off the chart. */}
          <text
            x={x(p.x) > width / 2 ? x(p.x) - 10 : x(p.x) + 10}
            y={y(p.y) + 4}
            textAnchor={x(p.x) > width / 2 ? 'end' : 'start'}
            fontSize="11"
            fill={INK}
          >
            {p.label}
          </text>
          <title>{`${p.label}: ${formatX(p.x)}, rated ${p.y.toFixed(1)}`}</title>
        </g>
      ))}
    </svg>
  );
}
