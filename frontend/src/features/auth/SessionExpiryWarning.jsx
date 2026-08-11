import { useEffect, useRef } from 'react';
import { useSelector } from 'react-redux';
import toast from 'react-hot-toast';
import { AlarmClock } from 'lucide-react';
import { tokenStore } from '../../api/tokenStore';
import { decodeJwtExpiry } from '../../utils/jwt';

const WARN_BEFORE_MS = 60_000;
const POLL_MS = 15_000;

// Purely informational — the axios interceptor (S6.1) already refreshes silently on the next
// 401, so this doesn't need to DO anything, just tell the user their access token is about to
// roll over in case a long-idle tab's refresh token has also since expired or been revoked
// elsewhere, in which case the next request fails closed and ProtectedRoute sends them to
// /login with the current location preserved (state.from), same as any other 401.
//
// Polls rather than a single setTimeout because the token itself rotates on every silent
// refresh — a one-shot timer armed at mount would only ever fire for the very first access
// token of the session, not the ones issued afterward.
export default function SessionExpiryWarning() {
  const isAuthenticated = useSelector((state) => state.auth.isAuthenticated);
  const warnedForExpiry = useRef(null);

  useEffect(() => {
    if (!isAuthenticated) return;

    const check = () => {
      const token = tokenStore.getAccessToken();
      const expiresAt = token ? decodeJwtExpiry(token) : null;
      if (!expiresAt) return;

      const msLeft = expiresAt - Date.now();
      if (msLeft > 0 && msLeft <= WARN_BEFORE_MS && warnedForExpiry.current !== expiresAt) {
        warnedForExpiry.current = expiresAt;
        toast(
          <span className="flex items-center gap-2">
            <AlarmClock className="h-4 w-4 shrink-0 text-amber-500" aria-hidden="true" />
            Your session is refreshing shortly — stay active to avoid being signed out.
          </span>,
          { duration: 6000 },
        );
      }
    };

    check();
    const interval = setInterval(check, POLL_MS);
    return () => clearInterval(interval);
  }, [isAuthenticated]);

  return null;
}
