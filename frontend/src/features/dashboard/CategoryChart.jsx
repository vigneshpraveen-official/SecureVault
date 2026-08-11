import Card from '../../components/Card';
import Skeleton from '../../components/Skeleton';
import EmptyState from '../../components/EmptyState';
import { PieChart } from 'lucide-react';

const BAR_COLOR = {
  PERSONAL: 'bg-accent-500',
  WORK: 'bg-neutral-500',
  DEVELOPMENT: 'bg-green-500',
  SOCIAL: 'bg-amber-500',
  BANKING: 'bg-red-500',
  ENTERTAINMENT: 'bg-violet-500',
  OTHER: 'bg-neutral-400',
};

// Deliberately plain-SVG-free here — a horizontal bar list conveys a category breakdown just as
// clearly as a donut for 7 categories, without hand-rolled arc trigonometry. See docs/decisions.md
// for the "plain markup over a charting library" ADR (S6.6).
export default function CategoryChart({ byCategory, loading }) {
  if (loading) {
    return (
      <Card className="p-5">
        <Skeleton className="h-4 w-32" />
        <Skeleton className="mt-4 h-24 w-full" />
      </Card>
    );
  }

  const entries = Object.entries(byCategory ?? {}).filter(([, count]) => count > 0);
  const total = entries.reduce((sum, [, count]) => sum + count, 0);

  return (
    <Card className="p-5">
      <h2 className="text-sm font-medium text-neutral-500">By category</h2>
      {total === 0 ? (
        <EmptyState icon={PieChart} title="No credentials yet" />
      ) : (
        <ul className="mt-3 flex flex-col gap-2">
          {entries
            .sort(([, a], [, b]) => b - a)
            .map(([category, count]) => (
              <li key={category} className="flex items-center gap-2 text-sm">
                <span className="w-28 shrink-0 truncate text-neutral-600">{category}</span>
                <div className="h-2 flex-1 overflow-hidden rounded-full bg-neutral-100">
                  <div
                    className={`h-full rounded-full ${BAR_COLOR[category] ?? 'bg-neutral-400'}`}
                    style={{ width: `${(count / total) * 100}%` }}
                  />
                </div>
                <span className="w-6 shrink-0 text-right text-neutral-500">{count}</span>
              </li>
            ))}
        </ul>
      )}
    </Card>
  );
}
