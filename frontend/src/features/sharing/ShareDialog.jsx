import { useState } from 'react';
import toast from 'react-hot-toast';
import Modal from '../../components/Modal';
import Input from '../../components/Input';
import Button from '../../components/Button';
import { sharingApi } from '../../api/sharing';

export default function ShareDialog({ open, onClose, credential, onShared }) {
  const [email, setEmail] = useState('');
  const [permission, setPermission] = useState('READ');
  const [expiresAt, setExpiresAt] = useState('');
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  function reset() {
    setEmail('');
    setPermission('READ');
    setExpiresAt('');
    setError(null);
  }

  async function handleSubmit(event) {
    event.preventDefault();
    if (submitting) return;
    setSubmitting(true);
    setError(null);
    try {
      await sharingApi.create({
        credentialId: credential.id,
        sharedWithEmail: email,
        permission,
        expiresAt: expiresAt ? new Date(expiresAt).toISOString() : null,
      });
      toast.success(`Shared "${credential.title}" with ${email}.`);
      reset();
      onShared?.();
      onClose();
    } catch (apiError) {
      // SELF_SHARE_NOT_ALLOWED, SHARE_ALREADY_EXISTS, USER_NOT_FOUND all arrive as
      // apiError.message from the backend (docs/ai/CONVENTIONS.md) — shown verbatim rather
      // than re-worded, so the message always matches what the server actually decided.
      setError(apiError.message ?? 'Could not share this credential.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal
      open={open}
      onClose={() => {
        reset();
        onClose();
      }}
      title={`Share "${credential?.title ?? ''}"`}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={submitting}>
            Cancel
          </Button>
          <Button type="submit" form="share-form" loading={submitting}>
            Share
          </Button>
        </>
      }
    >
      <form id="share-form" onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
        <Input
          label="Recipient email"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
        />
        <div className="flex flex-col gap-1">
          <label htmlFor="share-permission" className="text-sm font-medium text-neutral-700">
            Permission
          </label>
          <select
            id="share-permission"
            value={permission}
            onChange={(e) => setPermission(e.target.value)}
            className="rounded-sv border border-neutral-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-accent-500"
          >
            <option value="READ">Read only — view password</option>
            <option value="EDIT">Edit — view and update</option>
          </select>
        </div>
        <Input
          label="Expires (optional)"
          type="date"
          value={expiresAt}
          onChange={(e) => setExpiresAt(e.target.value)}
          min={new Date().toISOString().split('T')[0]}
        />
        {error && <p className="text-sm text-danger-600">{error}</p>}
      </form>
    </Modal>
  );
}
