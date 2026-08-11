import { useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import { CheckCircle2, XCircle } from 'lucide-react';
import Card from '../../components/Card';
import Table from '../../components/Table';
import Badge from '../../components/Badge';
import Spinner from '../../components/Spinner';
import { monitoringApi } from '../../api/monitoring';

const SEVERITY_VARIANT = { LOW: 'neutral', MEDIUM: 'warning', HIGH: 'danger' };

// "Active devices, platform-wide" was asked for in the S6.7 prompt but the backend (P5.4) only
// ever exposes GET /api/monitoring/devices for the CALLER's own devices — there is no admin-scoped
// "all devices" endpoint (unlike login-attempts/alerts, which both support ?all=true). Documented
// as a known gap rather than faked with per-user device lookups this page has no reason to make.
export default function AdminSecurityMonitoring() {
  const [attempts, setAttempts] = useState(null);
  const [alerts, setAlerts] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([monitoringApi.loginAttempts(true), monitoringApi.alerts(true)])
      .then(([a, b]) => {
        setAttempts(a);
        setAlerts(b);
      })
      .catch(() => toast.error('Could not load security monitoring data.'))
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return (
      <div className="flex justify-center py-16">
        <Spinner />
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <Card className="p-4">
        <h2 className="mb-3 text-sm font-medium text-neutral-500">Login attempts (all users)</h2>
        {!attempts?.length ? (
          <p className="py-4 text-center text-sm text-neutral-500">No login attempts recorded.</p>
        ) : (
          <Table columns={[{ key: 'result', label: '' }, { key: 'email', label: 'Email' }, { key: 'ip', label: 'IP' }, { key: 'time', label: 'When' }, { key: 'reason', label: 'Reason' }]}>
            {attempts.slice(0, 50).map((a) => (
              <tr key={a.id} className="hover:bg-neutral-50">
                <td className="px-4 py-2">
                  {a.successful ? (
                    <CheckCircle2 className="h-4 w-4 text-green-600" aria-label="Success" />
                  ) : (
                    <XCircle className="h-4 w-4 text-danger-600" aria-label="Failed" />
                  )}
                </td>
                <td className="px-4 py-2 text-neutral-700">{a.email}</td>
                <td className="px-4 py-2 text-neutral-500">{a.ipAddress}</td>
                <td className="px-4 py-2 text-neutral-500">{new Date(a.attemptedAt).toLocaleString()}</td>
                <td className="px-4 py-2 text-neutral-500">{a.failureReason ?? '—'}</td>
              </tr>
            ))}
          </Table>
        )}
      </Card>

      <Card className="p-4">
        <h2 className="mb-3 text-sm font-medium text-neutral-500">Security alerts (all users)</h2>
        {!alerts?.length ? (
          <p className="py-4 text-center text-sm text-neutral-500">No unresolved alerts.</p>
        ) : (
          <ul className="flex flex-col gap-2">
            {alerts.map((alert) => (
              <li key={alert.id} className="flex items-center justify-between rounded-sv border border-neutral-200 p-3 text-sm">
                <span className="flex items-center gap-2">
                  <Badge variant={SEVERITY_VARIANT[alert.severity]}>{alert.severity}</Badge>
                  {alert.message}
                </span>
                <span className="text-xs text-neutral-400">{new Date(alert.createdAt).toLocaleString()}</span>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  );
}
