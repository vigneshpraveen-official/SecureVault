// Client-side decode ONLY — this never verifies the signature (the server already did, and
// the browser has no key to check it with anyway). Used purely to read the exp claim for UX
// timing (S6.8's session-expiry warning), never for any authorization decision.
export function decodeJwtExpiry(token) {
  try {
    const payload = token.split('.')[1];
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
    const { exp } = JSON.parse(json);
    return exp ? exp * 1000 : null;
  } catch {
    return null;
  }
}
