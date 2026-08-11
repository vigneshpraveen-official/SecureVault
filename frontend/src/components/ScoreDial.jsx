const RADIUS = 32;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

function scoreColor(score) {
  if (score >= 80) return '#22c55e';
  if (score >= 50) return '#f59e0b';
  return '#ef4444';
}

export default function ScoreDial({ score, size = 80 }) {
  const offset = CIRCUMFERENCE - (score / 100) * CIRCUMFERENCE;
  return (
    <svg width={size} height={size} viewBox="0 0 80 80" role="img" aria-label={`Score ${score} out of 100`}>
      <circle cx={40} cy={40} r={RADIUS} fill="none" stroke="#e2e8f0" strokeWidth={8} />
      <circle
        cx={40}
        cy={40}
        r={RADIUS}
        fill="none"
        stroke={scoreColor(score)}
        strokeWidth={8}
        strokeDasharray={CIRCUMFERENCE}
        strokeDashoffset={offset}
        strokeLinecap="round"
        transform="rotate(-90 40 40)"
      />
      <text x={40} y={45} textAnchor="middle" className="fill-neutral-900 text-lg font-semibold">
        {score}
      </text>
    </svg>
  );
}
