import { useEffect, useState } from 'react';
import { ShieldAlert } from 'lucide-react';
import Card from '../../components/Card';
import Spinner from '../../components/Spinner';
import ScoreDial from '../../components/ScoreDial';
import { vaultApi } from '../../api/vault';

// Consumes GET /api/vault/health (S3.3) — the vault-scoped snapshot. The dashboard's richer
// "top 5 to fix" list (S6.6) comes from a different endpoint, GET /api/dashboard/password-health
// (S5.7), which is the only one that actually returns per-credential fix suggestions; this
// widget's source endpoint returns aggregate counts only, so it stops there deliberately.
export default function PasswordHealthWidget() {
  const [health, setHealth] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    vaultApi
      .health()
      .then((result) => !cancelled && setHealth(result))
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, []);

  if (loading) {
    return (
      <Card className="flex items-center justify-center p-6">
        <Spinner />
      </Card>
    );
  }

  if (!health || health.totalCredentials === 0) {
    return (
      <Card className="flex flex-col items-center gap-2 p-6 text-center text-sm text-neutral-500">
        <ShieldAlert className="h-6 w-6 text-neutral-400" aria-hidden="true" />
        Add credentials to see your password health.
      </Card>
    );
  }

  return (
    <Card className="flex items-center gap-5 p-5">
      <ScoreDial score={health.healthScore} />
      <dl className="grid flex-1 grid-cols-2 gap-x-4 gap-y-1 text-sm sm:grid-cols-4">
        <div>
          <dt className="text-neutral-500">Weak</dt>
          <dd className="font-semibold text-red-600">{health.veryWeakCount + health.weakCount}</dd>
        </div>
        <div>
          <dt className="text-neutral-500">Reused</dt>
          <dd className="font-semibold text-amber-600">{health.reusedPasswordCount}</dd>
        </div>
        <div>
          <dt className="text-neutral-500">Stale (90d+)</dt>
          <dd className="font-semibold text-amber-600">{health.staleCredentialCount}</dd>
        </div>
        <div>
          <dt className="text-neutral-500">Total</dt>
          <dd className="font-semibold text-neutral-800">{health.totalCredentials}</dd>
        </div>
      </dl>
    </Card>
  );
}
