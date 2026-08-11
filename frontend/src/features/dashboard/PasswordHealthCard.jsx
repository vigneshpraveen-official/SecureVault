import { Link } from 'react-router-dom';
import Card from '../../components/Card';
import Skeleton from '../../components/Skeleton';
import ScoreDial from '../../components/ScoreDial';
import EmptyState from '../../components/EmptyState';
import { ShieldCheck } from 'lucide-react';

export default function PasswordHealthCard({ health, loading }) {
  if (loading) {
    return (
      <Card className="p-5">
        <Skeleton className="mx-auto h-20 w-20 rounded-full" />
        <Skeleton className="mx-auto mt-4 h-4 w-32" />
      </Card>
    );
  }

  if (!health || health.veryWeakCount + health.weakCount + health.mediumCount + health.strongCount + health.veryStrongCount === 0) {
    return (
      <Card className="p-5">
        <EmptyState
          icon={ShieldCheck}
          title="No password health data yet"
          description="Add credentials to your vault to see a health breakdown."
        />
      </Card>
    );
  }

  return (
    <Card className="p-5">
      <h2 className="text-sm font-medium text-neutral-500">Password health</h2>
      <div className="mt-3 flex items-center gap-5">
        <ScoreDial score={health.healthScore} />
        <dl className="grid flex-1 grid-cols-2 gap-x-4 gap-y-1 text-sm">
          <div>
            <dt className="text-neutral-500">Weak</dt>
            <dd className="font-semibold text-red-600">{health.veryWeakCount + health.weakCount}</dd>
          </div>
          <div>
            <dt className="text-neutral-500">Reused</dt>
            <dd className="font-semibold text-amber-600">{health.reusedPasswordCount}</dd>
          </div>
          <div>
            <dt className="text-neutral-500">Stale</dt>
            <dd className="font-semibold text-amber-600">{health.staleCredentialCount}</dd>
          </div>
          <div>
            <dt className="text-neutral-500">Strong</dt>
            <dd className="font-semibold text-green-600">{health.strongCount + health.veryStrongCount}</dd>
          </div>
        </dl>
      </div>
      {health.topItemsToFix?.length > 0 && (
        <div className="mt-4 border-t border-neutral-200 pt-3">
          <p className="text-xs font-medium uppercase text-neutral-400">Fix these first</p>
          <ul className="mt-2 flex flex-col gap-1.5">
            {health.topItemsToFix.map((item) => (
              <li key={item.credentialId} className="flex items-center justify-between text-sm">
                <Link to="/vault" className="font-medium text-accent-600 hover:underline">
                  {item.title}
                </Link>
                <span className="text-neutral-500">{item.reason}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </Card>
  );
}
