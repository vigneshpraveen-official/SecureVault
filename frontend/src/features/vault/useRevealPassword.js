import { useCallback, useEffect, useRef, useState } from 'react';
import toast from 'react-hot-toast';
import { vaultApi } from '../../api/vault';

const REVEAL_MS = 20_000;
const CLIPBOARD_CLEAR_MS = 30_000;

// One hook instance per open row. Password only ever lives in this hook's local state —
// never in Redux, never in localStorage — and is force-cleared on both the reveal timeout
// and unmount so navigating away can't leave a stale plaintext value sitting in memory.
export default function useRevealPassword() {
  const [revealedId, setRevealedId] = useState(null);
  const [password, setPassword] = useState(null);
  const [loading, setLoading] = useState(false);
  const hideTimer = useRef(null);

  const hide = useCallback(() => {
    clearTimeout(hideTimer.current);
    setRevealedId(null);
    setPassword(null);
  }, []);

  useEffect(() => () => clearTimeout(hideTimer.current), []);

  const reveal = useCallback(
    async (credentialId) => {
      if (revealedId === credentialId) {
        hide();
        return;
      }
      setLoading(true);
      try {
        const detail = await vaultApi.getById(credentialId);
        setPassword(detail.password);
        setRevealedId(credentialId);
        clearTimeout(hideTimer.current);
        hideTimer.current = setTimeout(hide, REVEAL_MS);
      } catch {
        toast.error('Could not reveal this password.');
      } finally {
        setLoading(false);
      }
    },
    [revealedId, hide],
  );

  const copy = useCallback(async (value) => {
    if (!navigator.clipboard) {
      toast.error('Clipboard access is not available in this browser.');
      return;
    }
    try {
      await navigator.clipboard.writeText(value);
      toast.success('Copied — clipboard will clear in 30s.');
      // Best-effort only: if the user copies something else before this fires, that later
      // copy gets silently overwritten with an empty string. There is no clipboard API that
      // lets us clear "only if it's still ours." Documented in S6.3 evidence, not hidden.
      setTimeout(() => {
        navigator.clipboard.writeText('').catch(() => {});
      }, CLIPBOARD_CLEAR_MS);
    } catch {
      toast.error('Could not copy to clipboard.');
    }
  }, []);

  return { revealedId, password, loading, reveal, hide, copy };
}
