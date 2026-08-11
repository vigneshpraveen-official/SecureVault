import { useCallback, useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import { Eye, EyeOff, Copy, Pencil, Ban, Share2 } from 'lucide-react';
import Card from '../components/Card';
import Badge from '../components/Badge';
import Table from '../components/Table';
import Spinner from '../components/Spinner';
import EmptyState from '../components/EmptyState';
import ConfirmDialog from '../components/ConfirmDialog';
import IconButton from '../components/IconButton';
import Tabs from '../components/Tabs';
import { sharingApi } from '../api/sharing';
import { vaultApi } from '../api/vault';
import { expiryLabel } from '../features/sharing/expiry';
import useRevealPassword from '../features/vault/useRevealPassword';
import CredentialFormModal from '../features/vault/CredentialFormModal';

const TABS = [
  { key: 'received', label: 'Shared with me' },
  { key: 'sent', label: 'Shared by me' },
];

function PermissionBadge({ permission }) {
  return <Badge variant={permission === 'EDIT' ? 'accent' : 'neutral'}>{permission}</Badge>;
}

function ReceivedTab() {
  const [shares, setShares] = useState(null);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(null);
  const [editingCredentialId, setEditingCredentialId] = useState(null);
  const [savingEdit, setSavingEdit] = useState(false);
  const reveal = useRevealPassword();

  const load = useCallback(() => {
    setLoading(true);
    sharingApi
      .received()
      .then(setShares)
      .catch(() => toast.error('Could not load shares.'))
      .finally(() => setLoading(false));
  }, []);

  useEffect(load, [load]);

  async function handleCopy(id) {
    try {
      const detail = await vaultApi.getById(id);
      await reveal.copy(detail.password);
    } catch (error) {
      toast.error(error.message ?? 'Could not copy password.');
    }
  }

  async function handleOpenEdit(credentialId) {
    try {
      // ShareResponse only carries title/owner/permission — the real username/website/notes/
      // category live on the credential itself, so fetch it fresh rather than pre-filling the
      // form with blanks that would then get written back as real (empty) values on save.
      // The reveal endpoint also returns the decrypted password — discarded immediately rather
      // than held in this component's state, since the edit form never needs or displays it.
      const { password: _password, ...detailWithoutPassword } = await vaultApi.getById(credentialId);
      setEditing(detailWithoutPassword);
      setEditingCredentialId(credentialId);
    } catch (error) {
      toast.error(error.message ?? 'Could not load this credential.');
    }
  }

  async function handleEditSubmit(payload) {
    setSavingEdit(true);
    try {
      await vaultApi.update(editingCredentialId, payload);
      toast.success('Credential updated.');
    } finally {
      setSavingEdit(false);
    }
  }

  if (loading) {
    return (
      <div className="flex justify-center py-16">
        <Spinner />
      </div>
    );
  }

  if (!shares?.length) {
    return (
      <EmptyState
        icon={Share2}
        title="Nothing shared with you yet"
        description="Credentials someone shares with you will show up here."
      />
    );
  }

  return (
    <>
      <Table columns={[{ key: 'title', label: 'Credential' }, { key: 'owner', label: 'Shared by' }, { key: 'permission', label: 'Permission' }, { key: 'expiry', label: 'Expiry' }, { key: 'actions', label: '' }]}>
        {shares.map((share) => (
          <tr key={share.id} className="hover:bg-neutral-50">
            <td className="px-4 py-3">
              <div className="flex items-center gap-2 font-medium text-neutral-900">
                {share.credentialTitle}
                <Badge variant="neutral">Shared</Badge>
              </div>
            </td>
            <td className="px-4 py-3 text-neutral-600">{share.ownerEmail}</td>
            <td className="px-4 py-3">
              <PermissionBadge permission={share.permission} />
            </td>
            <td className="px-4 py-3 text-neutral-500">{expiryLabel(share.expiresAt, false)}</td>
            <td className="px-4 py-3">
              <div className="flex items-center justify-end gap-1">
                <IconButton
                  icon={reveal.revealedId === share.credentialId ? EyeOff : Eye}
                  label={reveal.revealedId === share.credentialId ? 'Hide password' : 'Reveal password (auto-hides in 20s)'}
                  onClick={() => reveal.reveal(share.credentialId)}
                />
                <IconButton icon={Copy} label="Copy password" onClick={() => handleCopy(share.credentialId)} />
                {/* EDIT-only affordance — a READ share never renders this button; the server
                    enforces the same rule independently via AccessEvaluator (P5.1), so hiding
                    it here is a UX nicety, not the security boundary. */}
                {share.permission === 'EDIT' && (
                  <IconButton icon={Pencil} label="Edit credential" onClick={() => handleOpenEdit(share.credentialId)} />
                )}
              </div>
              {reveal.revealedId === share.credentialId && (
                <div className="mt-1 rounded-sv bg-neutral-100 px-2 py-1 text-right font-mono text-xs text-neutral-800">
                  {reveal.password}
                </div>
              )}
            </td>
          </tr>
        ))}
      </Table>
      <CredentialFormModal
        open={Boolean(editing)}
        onClose={() => {
          setEditing(null);
          setEditingCredentialId(null);
        }}
        onSubmit={handleEditSubmit}
        submitting={savingEdit}
        initialValues={editing}
      />
    </>
  );
}

function SentTab() {
  const [shares, setShares] = useState(null);
  const [loading, setLoading] = useState(true);
  const [revokeTarget, setRevokeTarget] = useState(null);
  const [revoking, setRevoking] = useState(false);

  const load = useCallback(() => {
    setLoading(true);
    sharingApi
      .sent()
      .then(setShares)
      .catch(() => toast.error('Could not load shares.'))
      .finally(() => setLoading(false));
  }, []);

  useEffect(load, [load]);

  async function handlePermissionChange(share, permission) {
    try {
      await sharingApi.updatePermission(share.id, permission);
      toast.success('Permission updated.');
      load();
    } catch (error) {
      toast.error(error.message ?? 'Could not update permission.');
    }
  }

  async function handleRevoke() {
    if (!revokeTarget) return;
    setRevoking(true);
    try {
      await sharingApi.revoke(revokeTarget.id);
      toast.success('Share revoked.');
      setRevokeTarget(null);
      load();
    } catch (error) {
      toast.error(error.message ?? 'Could not revoke share.');
    } finally {
      setRevoking(false);
    }
  }

  if (loading) {
    return (
      <div className="flex justify-center py-16">
        <Spinner />
      </div>
    );
  }

  if (!shares?.length) {
    return (
      <EmptyState
        icon={Share2}
        title="You haven't shared anything"
        description="Share a credential from the Vault page to see it here."
      />
    );
  }

  return (
    <>
      <Table columns={[{ key: 'title', label: 'Credential' }, { key: 'recipient', label: 'Recipient' }, { key: 'permission', label: 'Permission' }, { key: 'expiry', label: 'Expiry' }, { key: 'actions', label: '' }]}>
        {shares.map((share) => (
          <tr key={share.id} className="hover:bg-neutral-50">
            <td className="px-4 py-3 font-medium text-neutral-900">{share.credentialTitle}</td>
            <td className="px-4 py-3 text-neutral-600">{share.sharedWithEmail}</td>
            <td className="px-4 py-3">
              <select
                value={share.permission}
                onChange={(e) => handlePermissionChange(share, e.target.value)}
                disabled={share.expired}
                className="rounded-sv border border-neutral-300 px-2 py-1 text-xs focus:outline-none focus:ring-2 focus:ring-accent-500 disabled:opacity-50"
              >
                <option value="READ">READ</option>
                <option value="EDIT">EDIT</option>
              </select>
            </td>
            <td className="px-4 py-3">
              <span className={share.expired ? 'text-danger-600' : 'text-neutral-500'}>
                {expiryLabel(share.expiresAt, share.expired)}
              </span>
            </td>
            <td className="px-4 py-3 text-right">
              <IconButton icon={Ban} label="Revoke share" variant="danger" onClick={() => setRevokeTarget(share)} />
            </td>
          </tr>
        ))}
      </Table>
      <ConfirmDialog
        open={Boolean(revokeTarget)}
        onClose={() => setRevokeTarget(null)}
        onConfirm={handleRevoke}
        loading={revoking}
        title="Revoke share?"
        description={`${revokeTarget?.sharedWithEmail} will immediately lose access to "${revokeTarget?.credentialTitle}".`}
        confirmLabel="Revoke"
      />
    </>
  );
}

export default function SharingPage() {
  const [tab, setTab] = useState('received');

  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-xl font-semibold text-neutral-900">Sharing</h1>
      <Tabs tabs={TABS} active={tab} onChange={setTab} />
      <Card>{tab === 'received' ? <ReceivedTab /> : <SentTab />}</Card>
    </div>
  );
}
