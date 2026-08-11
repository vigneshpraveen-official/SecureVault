// Mirrors backend/src/main/java/com/securevault/user/dto/UserRegisterRequest.java exactly
// (docs/validation.md) so a client-side rejection always matches what the server would say —
// this is a UX convenience layer only; the server re-validates everything regardless.
const COMPLEXITY = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^a-zA-Z0-9]).+$/;
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function validateRegisterForm({ fullName, email, password, confirmPassword }) {
  const errors = {};

  if (!fullName?.trim()) errors.fullName = 'Full name is required.';
  else if (fullName.length > 100) errors.fullName = 'Full name must be 100 characters or fewer.';

  if (!email?.trim()) errors.email = 'Email is required.';
  else if (!EMAIL.test(email)) errors.email = 'Enter a valid email address.';
  else if (email.length > 150) errors.email = 'Email must be 150 characters or fewer.';

  if (!password) errors.password = 'Password is required.';
  else if (password.length < 8 || password.length > 72) {
    errors.password = 'Password must be between 8 and 72 characters.';
  } else if (!COMPLEXITY.test(password)) {
    errors.password = 'Password needs an uppercase letter, a lowercase letter, a digit, and a special character.';
  }

  if (confirmPassword !== password) errors.confirmPassword = 'Passwords do not match.';

  return errors;
}
