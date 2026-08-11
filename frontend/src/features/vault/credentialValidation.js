// Mirrors CredentialCreateRequest/CredentialUpdateRequest (docs/validation.md) — password has
// no complexity @Pattern deliberately (it's a secret for a site SecureVault doesn't control).
const URL_PATTERN = /^https?:\/\/.+/i;

export function validateCredentialForm({ title, username, password, websiteUrl, notes }, isCreate) {
  const errors = {};

  if (!title?.trim()) errors.title = 'Title is required.';
  else if (title.length > 150) errors.title = 'Title must be 150 characters or fewer.';

  if (username && username.length > 150) errors.username = 'Username must be 150 characters or fewer.';

  if (isCreate && !password) errors.password = 'Password is required.';

  if (websiteUrl && (!URL_PATTERN.test(websiteUrl) || websiteUrl.length > 255)) {
    errors.websiteUrl = 'Enter a valid URL (starting with http:// or https://), up to 255 characters.';
  }

  if (notes && notes.length > 2000) errors.notes = 'Notes must be 2000 characters or fewer.';

  return errors;
}
