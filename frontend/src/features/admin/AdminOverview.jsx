import { useEffect, useState } from 'react';
import { Users, Activity, ShieldAlert, HeartPulse } from 'lucide-react';
import Card from '../../components/Card';
import Skeleton from '../../components/Skeleton';
import Badge from '../../components/Badge';
import { adminApi } from '../../api/admin';

const SEVERITY_VARIANT = { LOW: 'neutral', MEDIUM: 'warning', HIGH: 'danger' };

export default function AdminOverview() {
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    adminApi
      .stats()
      .then(setStats)
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return (
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        {Array.from({ length: 4 }).map((_, i) => (
          <Card key={i} className="p-4">
            <Skeleton className="h-4 w-20" />
            <Skeleton className="mt-2 h-7 w-10" />
          </Card>
        ))}
      </div>
    );
  }

  const cards = [
    { label: 'Total users', value: stats.totalUsers, icon: Users },
    { label: 'Active sessions', value: stats.activeSessions, icon: Activity },
    { label: 'Failed logins (24h)', value: stats.failedLogins24h, icon: ShieldAlert },
    { label: 'System health', value: stats.systemHealth, icon: HeartPulse },
  ];

  return (
    <div className="flex flex-col gap-4">
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        {cards.map(({ label, value, icon: Icon }) => (
          <Card key={label} className="p-4">
            <div className="flex items-center gap-2 text-neutral-500">
              <Icon className="h-4 w-4" aria-hidden="true" />
              <span className="text-sm">{label}</span>
            </div>
            <p className="mt-1 text-2xl font-semibold text-neutral-900">{value}</p>
          </Card>
        ))}
      </div>
      <Card className="p-5">
        <h2 className="text-sm font-medium text-neutral-500">Unresolved alerts by severity</h2>
        <div className="mt-3 flex gap-2">
          {Object.entries(stats.unresolvedAlertsBySeverity ?? {}).length === 0 ? (
            <p className="text-sm text-neutral-500">None.</p>
          ) : (
            Object.entries(stats.unresolvedAlertsBySeverity).map(([severity, count]) => (
              <Badge key={severity} variant={SEVERITY_VARIANT[severity]}>
                {severity}: {count}
              </Badge>
            ))
          )}
        </div>
      </Card>
    </div>
  );
}
