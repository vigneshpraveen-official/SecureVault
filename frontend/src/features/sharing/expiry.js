export function expiryLabel(expiresAt, expired) {
  if (!expiresAt) return 'No expiry';
  if (expired) return 'Expired';

  const ms = new Date(expiresAt).getTime() - Date.now();
  const days = Math.ceil(ms / (1000 * 60 * 60 * 24));
  if (days <= 0) return 'Expires today';
  if (days === 1) return 'Expires tomorrow';
  return `Expires in ${days} days`;
}
