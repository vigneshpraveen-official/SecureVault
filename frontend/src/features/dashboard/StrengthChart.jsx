import Card from '../../components/Card';
import Skeleton from '../../components/Skeleton';
import EmptyState from '../../components/EmptyState';
import { BarChart3 } from 'lucide-react';

const BANDS = [
  { key: 'veryWeakCount', label: 'Very Weak', color: 'bg-red-500' },
  { key: 'weakCount', label: 'Weak', color: 'bg-orange-500' },
  { key: 'mediumCount', label: 'Medium', color: 'bg-amber-500' },
  { key: 'strongCount', label: 'Strong', color: 'bg-lime-500' },
  { key: 'veryStrongCount', label: 'Very Strong', color: 'bg-green-500' },
];

export default function StrengthChart({ health, loading }) {
  if (loading) {
    return (
      <Card className="p-5">
        <Skeleton className="h-4 w-32" />
        <Skeleton className="mt-4 h-24 w-full" />
      </Card>
    );
  }

  const total = BANDS.reduce((sum, b) => sum + (health?.[b.key] ?? 0), 0);

  return (
    <Card className="p-5">
      <h2 className="text-sm font-medium text-neutral-500">By strength</h2>
      {total === 0 ? (
        <EmptyState icon={BarChart3} title="No credentials yet" />
      ) : (
        <ul className="mt-3 flex flex-col gap-2">
          {BANDS.map(({ key, label, color }) => {
            const count = health?.[key] ?? 0;
            return (
              <li key={key} className="flex items-center gap-2 text-sm">
                <span className="w-24 shrink-0 truncate text-neutral-600">{label}</span>
                <div className="h-2 flex-1 overflow-hidden rounded-full bg-neutral-100">
                  <div className={`h-full rounded-full ${color}`} style={{ width: `${(count / total) * 100}%` }} />
                </div>
                <span className="w-6 shrink-0 text-right text-neutral-500">{count}</span>
              </li>
            );
          })}
        </ul>
      )}
    </Card>
  );
}
