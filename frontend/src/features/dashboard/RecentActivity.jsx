import { History } from 'lucide-react';
import Card from '../../components/Card';
import Skeleton from '../../components/Skeleton';
import EmptyState from '../../components/EmptyState';
import { relativeTime } from '../../utils/relativeTime';

export default function RecentActivity({ items, loading }) {
  return (
    <Card className="p-5">
      <h2 className="text-sm font-medium text-neutral-500">Recent activity</h2>
      <div className="mt-3">
        {loading ? (
          <div className="flex flex-col gap-2">
            {Array.from({ length: 4 }).map((_, i) => (
              <Skeleton key={i} className="h-5 w-full" />
            ))}
          </div>
        ) : !items?.length ? (
          <EmptyState icon={History} title="No activity yet" description="Actions you take in your vault show up here." />
        ) : (
          <ul className="flex flex-col divide-y divide-neutral-100">
            {items.map((entry) => (
              <li key={entry.id} className="flex items-center justify-between py-2 text-sm">
                <span className="text-neutral-700">{entry.description}</span>
                <span className="shrink-0 pl-3 text-xs text-neutral-400">{relativeTime(entry.timestamp)}</span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </Card>
  );
}
