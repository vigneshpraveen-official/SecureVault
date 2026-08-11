import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import toast from 'react-hot-toast';
import { Trash2, RotateCcw, AlertTriangle } from 'lucide-react';
import Card from '../components/Card';
import Table from '../components/Table';
import Button from '../components/Button';
import Spinner from '../components/Spinner';
import EmptyState from '../components/EmptyState';
import Modal from '../components/Modal';
import { vaultApi } from '../api/vault';

const CONFIRM_WORD = 'delete';

const COLUMNS = [
  { key: 'title', label: 'Title' },
  { key: 'category', label: 'Category' },
  // The trash list DTO (CredentialSummaryResponse) doesn't expose a dedicated deletedAt field,
  // only updatedAt — but a soft delete sets both in the same write, so for a trashed row
  // updatedAt IS the deletion timestamp in practice, not a fabricated stand-in.
  { key: 'deletedAt', label: 'Deleted' },
  { key: 'actions', label: '' },
];

export default function TrashPage() {
  const [items, setItems] = useState(null);
  const [loading, setLoading] = useState(true);
  const [restoringId, setRestoringId] = useState(null);
  const [permanentTarget, setPermanentTarget] = useState(null);
  const [confirmText, setConfirmText] = useState('');
  const [deleting, setDeleting] = useState(false);

  const load = useCallback(() => {
    setLoading(true);
    vaultApi
      .trash()
      .then(setItems)
      .catch(() => toast.error('Could not load trash.'))
      .finally(() => setLoading(false));
  }, []);

  useEffect(load, [load]);

  async function handleRestore(id) {
    setRestoringId(id);
    try {
      await vaultApi.restore(id);
      toast.success('Restored.');
      load();
    } catch (error) {
      toast.error(error.message ?? 'Could not restore.');
    } finally {
      setRestoringId(null);
    }
  }

  async function handlePermanentDelete() {
    if (!permanentTarget) return;
    setDeleting(true);
    try {
      await vaultApi.permanentDelete(permanentTarget.id);
      toast.success('Permanently deleted.');
      setPermanentTarget(null);
      setConfirmText('');
      load();
    } catch (error) {
      toast.error(error.message ?? 'Could not permanently delete.');
    } finally {
      setDeleting(false);
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-neutral-900">Trash</h1>
        <Link to="/vault" className="text-sm font-medium text-accent-600 hover:underline">
          Back to vault
        </Link>
      </div>

      <Card>
        {loading ? (
          <div className="flex justify-center py-16">
            <Spinner />
          </div>
        ) : !items?.length ? (
          <EmptyState icon={Trash2} title="Trash is empty" description="Deleted credentials show up here." />
        ) : (
          <Table columns={COLUMNS}>
            {items.map((cred) => (
              <tr key={cred.id} className="hover:bg-neutral-50">
                <td className="px-4 py-3 font-medium text-neutral-900">{cred.title}</td>
                <td className="px-4 py-3 text-neutral-500">{cred.category}</td>
                <td className="px-4 py-3 text-neutral-500">{new Date(cred.updatedAt).toLocaleString()}</td>
                <td className="px-4 py-3">
                  <div className="flex justify-end gap-2">
                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={() => handleRestore(cred.id)}
                      loading={restoringId === cred.id}
                    >
                      <RotateCcw className="h-3.5 w-3.5" aria-hidden="true" />
                      Restore
                    </Button>
                    <Button variant="danger" size="sm" onClick={() => setPermanentTarget(cred)}>
                      <Trash2 className="h-3.5 w-3.5" aria-hidden="true" />
                      Delete forever
                    </Button>
                  </div>
                </td>
              </tr>
            ))}
          </Table>
        )}
      </Card>

      <Modal
        open={Boolean(permanentTarget)}
        onClose={() => {
          setPermanentTarget(null);
          setConfirmText('');
        }}
        title="Permanently delete this credential?"
        footer={
          <>
            <Button
              variant="secondary"
              onClick={() => {
                setPermanentTarget(null);
                setConfirmText('');
              }}
              disabled={deleting}
            >
              Cancel
            </Button>
            <Button
              variant="danger"
              onClick={handlePermanentDelete}
              loading={deleting}
              disabled={confirmText !== CONFIRM_WORD}
            >
              Delete forever
            </Button>
          </>
        }
      >
        <div className="flex flex-col gap-3">
          <p className="flex items-start gap-2 text-sm text-danger-700">
            <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
            This cannot be undone. "{permanentTarget?.title}" and its entire password history will be
            destroyed permanently — there is no second trash, no recovery.
          </p>
          <label htmlFor="confirm-delete-forever" className="text-sm text-neutral-600">
            Type <strong>{CONFIRM_WORD}</strong> to confirm:
          </label>
          <input
            id="confirm-delete-forever"
            value={confirmText}
            onChange={(e) => setConfirmText(e.target.value)}
            autoComplete="off"
            className="rounded-sv border border-neutral-300 px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-danger-500"
          />
        </div>
      </Modal>
    </div>
  );
}
