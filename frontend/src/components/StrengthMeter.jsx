const LEVELS = [
  { label: 'Very Weak', color: 'bg-red-500' },
  { label: 'Weak', color: 'bg-orange-500' },
  { label: 'Medium', color: 'bg-amber-500' },
  { label: 'Strong', color: 'bg-lime-500' },
  { label: 'Very Strong', color: 'bg-green-500' },
];

// Score is always 0-5 from the backend (docs/password-policy.md); segment count is fixed at 5
// so the bar is a direct, literal picture of that score — never re-derived on the client.
export default function StrengthMeter({ score, strength, entropyBits, feedback = [] }) {
  const level = LEVELS[Math.min(Math.max(score - 1, 0), 4)] ?? LEVELS[0];

  return (
    <div className="flex flex-col gap-1.5" aria-live="polite">
      <div className="flex gap-1" role="img" aria-label={`Password strength: ${strength}`}>
        {Array.from({ length: 5 }).map((_, i) => (
          <span
            key={i}
            className={`h-1.5 flex-1 rounded-full ${i < score ? level.color : 'bg-neutral-200'}`}
          />
        ))}
      </div>
      <div className="flex items-center justify-between text-xs">
        <span className="font-medium text-neutral-700">{strength}</span>
        {typeof entropyBits === 'number' && (
          <span className="text-neutral-400">{entropyBits.toFixed(1)} bits entropy</span>
        )}
      </div>
      {feedback.length > 0 && (
        <ul className="list-inside list-disc text-xs text-neutral-500">
          {feedback.map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ul>
      )}
    </div>
  );
}
