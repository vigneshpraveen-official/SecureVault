import { ShieldAlert, ShieldCheck } from 'lucide-react';
import Card from '../../components/Card';
import Badge from '../../components/Badge';
import Skeleton from '../../components/Skeleton';
import EmptyState from '../../components/EmptyState';
import { relativeTime } from '../../utils/relativeTime';

const SEVERITY_VARIANT = { LOW: 'neutral', MEDIUM: 'warning', HIGH: 'danger' };

// Read-only by design: the backend (P5.5) never grew a "resolve/dismiss" endpoint — only an
// internal `resolved` flag that nothing sets from the API surface. A dismiss button here would
// call an endpoint that doesn't exist, so this panel shows unresolved alerts without one.
export default function SecurityAlerts({ alerts, loading }) {
  return (
    <Card className="p-5">
      <h2 className="text-sm font-medium text-neutral-500">Security alerts</h2>
      <div className="mt-3">
        {loading ? (
          <div className="flex flex-col gap-2">
            {Array.from({ length: 2 }).map((_, i) => (
              <Skeleton key={i} className="h-10 w-full" />
            ))}
          </div>
        ) : !alerts?.length ? (
          <EmptyState icon={ShieldCheck} title="No open alerts" description="Nothing needs your attention right now." />
        ) : (
          <ul className="flex flex-col gap-2">
            {alerts.map((alert) => (
              <li key={alert.id} className="flex items-start gap-3 rounded-sv border border-neutral-200 p-3">
                <ShieldAlert
                  className={`mt-0.5 h-4 w-4 shrink-0 ${
                    alert.severity === 'HIGH' ? 'text-danger-600' : alert.severity === 'MEDIUM' ? 'text-amber-500' : 'text-neutral-400'
                  }`}
                  aria-hidden="true"
                />
                <div className="flex-1">
                  <div className="flex items-center gap-2">
                    <Badge variant={SEVERITY_VARIANT[alert.severity]}>{alert.severity}</Badge>
                    <span className="text-xs text-neutral-400">{relativeTime(alert.createdAt)}</span>
                  </div>
                  <p className="mt-1 text-sm text-neutral-700">{alert.message}</p>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </Card>
  );
}
