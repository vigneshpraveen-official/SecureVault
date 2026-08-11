import { useCallback, useEffect, useState } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { Link, useSearchParams } from 'react-router-dom';
import toast from 'react-hot-toast';
import { Plus } from 'lucide-react';
import Card from '../components/Card';
import Button from '../components/Button';
import Table from '../components/Table';
import Pagination from '../components/Pagination';
import EmptyState from '../components/EmptyState';
import ConfirmDialog from '../components/ConfirmDialog';
import Spinner from '../components/Spinner';
import {
  fetchVaultList,
  createCredential,
  updateCredential,
  deleteCredential,
  restoreCredential,
  setQuery,
  DEFAULT_QUERY,
} from '../features/vault/vaultSlice';
import VaultFilters from '../features/vault/VaultFilters';
import VaultRow from '../features/vault/VaultRow';
import CredentialFormModal from '../features/vault/CredentialFormModal';
import useRevealPassword from '../features/vault/useRevealPassword';
import HistoryDrawer from '../features/vault/HistoryDrawer';
import PasswordHealthWidget from '../features/password/PasswordHealthWidget';
import ShareDialog from '../features/sharing/ShareDialog';
import { vaultApi } from '../api/vault';

const COLUMNS = [
  { key: 'title', label: 'Title' },
  { key: 'username', label: 'Username' },
  { key: 'category', label: 'Category' },
  { key: 'strength', label: 'Strength' },
  { key: 'updated', label: 'Updated' },
  { key: 'actions', label: '' },
];

export default function VaultPage() {
  const dispatch = useDispatch();
  const { query, page, status, error } = useSelector((state) => state.vault);
  const [searchParams, setSearchParams] = useSearchParams();
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [saving, setSaving] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [deleting, setDeleting] = useState(false);
  const [shareTarget, setShareTarget] = useState(null);
  const [historyTarget, setHistoryTarget] = useState(null);
  const reveal = useRevealPassword();

  // URL is the source of truth on first load (S6.1/S6.3: a filtered view must survive a
  // refresh and be shareable) — after that, query changes flow query→URL, not the reverse,
  // so typing in the search box doesn't fight the URL sync on every keystroke.
  useEffect(() => {
    const fromUrl = Object.fromEntries(searchParams.entries());
    if (Object.keys(fromUrl).length > 0) {
      dispatch(setQuery({ ...DEFAULT_QUERY, ...fromUrl, page: Number(fromUrl.page ?? 0) }));
    } else {
      dispatch(fetchVaultList(DEFAULT_QUERY));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    dispatch(fetchVaultList(query));
    const params = Object.fromEntries(
      Object.entries(query).filter(([k, v]) => v !== '' && !(k === 'page' && v === 0)),
    );
    setSearchParams(params, { replace: true });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [query]);

  // A failed list fetch must be visibly distinct from "you genuinely have zero credentials" —
  // without this, the empty state silently lies about why the table is empty (S6.8).
  useEffect(() => {
    if (error) toast.error(error.message ?? 'Could not load your vault.');
  }, [error]);

  function handleFilterChange(partial) {
    dispatch(setQuery(partial));
  }

  async function handleCreateOrUpdate(payload) {
    setSaving(true);
    try {
      if (editing) {
        await dispatch(updateCredential({ id: editing.id, payload })).unwrap();
        toast.success('Credential updated.');
      } else {
        await dispatch(createCredential(payload)).unwrap();
        toast.success('Credential added.');
      }
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete() {
    if (!deleteTarget) return;
    setDeleting(true);
    const { id, title } = deleteTarget;
    try {
      await dispatch(deleteCredential(id)).unwrap();
      setDeleteTarget(null);
      toast(
        (t) => (
          <span className="flex items-center gap-3">
            "{title}" moved to trash.
            <button
              className="font-medium text-accent-600 hover:underline"
              onClick={async () => {
                toast.dismiss(t.id);
                await dispatch(restoreCredential(id)).unwrap();
                toast.success('Restored.');
              }}
            >
              Undo
            </button>
          </span>
        ),
        { duration: 8000 },
      );
    } catch (error) {
      toast.error(error.message ?? 'Could not delete credential.');
    } finally {
      setDeleting(false);
    }
  }

  // Stable references so VaultRow's React.memo can actually skip re-rendering untouched rows
  // (e.g. when only one row's reveal state changes) instead of comparing new closures every render.
  const handleReveal = useCallback((id) => reveal.reveal(id), [reveal]);
  const handleCopy = useCallback(
    async (id) => {
      try {
        const detail = await vaultApi.getById(id);
        await reveal.copy(detail.password);
      } catch {
        toast.error('Could not copy password.');
      }
    },
    [reveal],
  );
  const handleHistory = useCallback((cred) => setHistoryTarget(cred), []);
  const handleShare = useCallback((cred) => setShareTarget(cred), []);
  const handleEditRow = useCallback((cred) => {
    setEditing(cred);
    setFormOpen(true);
  }, []);
  const handleDeleteRow = useCallback((cred) => setDeleteTarget(cred), []);

  const rows = page?.content ?? [];
  const isEmpty = status === 'idle' && rows.length === 0;
  const hasFilters = query.title || query.username || query.website || query.category;

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-neutral-900">Vault</h1>
        <div className="flex items-center gap-3">
          <Link to="/vault/trash" className="text-sm font-medium text-neutral-500 hover:text-neutral-800">
            Trash
          </Link>
          <Button
            onClick={() => {
              setEditing(null);
              setFormOpen(true);
            }}
          >
            <Plus className="h-4 w-4" aria-hidden="true" />
            Add credential
          </Button>
        </div>
      </div>

      <PasswordHealthWidget />

      <Card className="p-4">
        <VaultFilters query={query} onChange={handleFilterChange} />
      </Card>

      <Card>
        {status === 'loading' && rows.length === 0 ? (
          <div className="flex justify-center py-16">
            <Spinner />
          </div>
        ) : error && rows.length === 0 ? (
          <EmptyState
            title="Couldn't load your vault"
            description="Something went wrong reaching the server. Try again in a moment."
            action={<Button onClick={() => dispatch(fetchVaultList(query))}>Retry</Button>}
          />
        ) : isEmpty ? (
          <EmptyState
            title={hasFilters ? 'No credentials match' : 'You have no credentials'}
            description={
              hasFilters
                ? 'Try a different search term or clear your filters.'
                : 'Add your first credential to start building your vault.'
            }
            action={
              !hasFilters && (
                <Button onClick={() => setFormOpen(true)}>
                  <Plus className="h-4 w-4" aria-hidden="true" />
                  Add credential
                </Button>
              )
            }
          />
        ) : (
          <>
            <Table columns={COLUMNS}>
              {rows.map((cred) => (
                <VaultRow
                  key={cred.id}
                  cred={cred}
                  isRevealed={reveal.revealedId === cred.id}
                  revealedPassword={reveal.password}
                  onReveal={handleReveal}
                  onCopy={handleCopy}
                  onHistory={handleHistory}
                  onShare={handleShare}
                  onEdit={handleEditRow}
                  onDelete={handleDeleteRow}
                />
              ))}
            </Table>
            {page && (
              <Pagination
                page={page.currentPage}
                totalPages={page.totalPages}
                totalElements={page.totalElements}
                pageSize={page.pageSize}
                onPageChange={(p) => dispatch(setQuery({ page: p }))}
              />
            )}
          </>
        )}
      </Card>

      <CredentialFormModal
        open={formOpen}
        onClose={() => setFormOpen(false)}
        onSubmit={handleCreateOrUpdate}
        submitting={saving}
        initialValues={editing}
      />

      <ConfirmDialog
        open={Boolean(deleteTarget)}
        onClose={() => setDeleteTarget(null)}
        onConfirm={handleDelete}
        loading={deleting}
        title="Delete credential?"
        description={`"${deleteTarget?.title}" will be moved to trash. You can restore it from there, or undo right after deleting.`}
        confirmLabel="Delete"
      />

      <ShareDialog open={Boolean(shareTarget)} onClose={() => setShareTarget(null)} credential={shareTarget} />

      <HistoryDrawer
        open={Boolean(historyTarget)}
        onClose={() => setHistoryTarget(null)}
        credential={historyTarget}
      />
    </div>
  );
}
