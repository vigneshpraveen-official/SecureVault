import { useEffect, useState } from 'react';
import { ShieldCheck } from 'lucide-react';
import Modal from '../../components/Modal';
import Spinner from '../../components/Spinner';
import { vaultApi } from '../../api/vault';

export default function HistoryDrawer({ open, onClose, credential }) {
  const [versions, setVersions] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!open || !credential) return;
    setLoading(true);
    setVersions(null);
    vaultApi
      .history(credential.id)
      .then(setVersions)
      .finally(() => setLoading(false));
  }, [open, credential]);

  return (
    <Modal open={open} onClose={onClose} title={`Password history — ${credential?.title ?? ''}`}>
      <div className="flex items-start gap-2 rounded-sv bg-accent-50 p-3 text-sm text-accent-800">
        <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
        <p>
          Only version numbers and dates are shown — this is by design. Even you, the owner, can't
          retrieve a past password once it's been changed. That guarantee only matters if it holds
          for everyone, no exceptions.
        </p>
      </div>
      {loading ? (
        <div className="flex justify-center py-8">
          <Spinner />
        </div>
      ) : versions?.length ? (
        <ul className="mt-3 divide-y divide-neutral-200">
          {versions.map((v) => (
            <li key={v.version} className="flex items-center justify-between py-2 text-sm">
              <span className="font-medium text-neutral-800">Version {v.version}</span>
              <span className="text-neutral-500">{new Date(v.createdAt).toLocaleString()}</span>
            </li>
          ))}
        </ul>
      ) : (
        <p className="mt-3 text-sm text-neutral-500">No password changes yet.</p>
      )}
    </Modal>
  );
}
