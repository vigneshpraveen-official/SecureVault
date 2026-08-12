# S7.4 — Frontend tests: evidence

Vitest + React Testing Library + MSW (network-boundary mocking, per the prompt's explicit
instruction — not stubbing `api/*.js` modules).

## Test files
| File | Covers |
|---|---|
| `src/pages/LoginPage.test.jsx` | submit button disabled while the login request is pending; the backend's real error message surfaced on 401; MFA-challenge form shown when the login response reports `mfaRequired` |
| `src/pages/VaultPage.test.jsx` | one row rendered per credential; genuinely-empty-vault state vs. a distinct fetch-error state (never the same message); search input's debounced value reaches the request as the `title` query param; reveal fetches the decrypted password only on click, never on initial render |
| `src/features/vault/CredentialFormModal.test.jsx` | create submits exactly the expected payload shape; client-side validation blocks submission on a blank title without calling `onSubmit`; the strength meter renders the level/feedback returned by a mocked `POST /api/password/strength`; editing and leaving the password blank omits the field entirely (not an empty string) |
| `src/components/StrengthMeter.test.jsx` | renders the given level, entropy, and feedback list from props; no list rendered when feedback is empty |
| `src/components/ProtectedRoute.test.jsx` | redirects to `/login` when unauthenticated; renders the protected route's content when authenticated |

## Two real test-harness bugs found and fixed (documented inline in the affected test files, not application code)
1. **Missing RTL cleanup between tests.** This project runs Vitest with `globals: false`
   (`vitest.config.js`), so React Testing Library's auto-cleanup-after-each-test never
   self-registered. Every test in a file was rendering on top of the previous test's still
   -mounted DOM — the second `CredentialFormModal` test's password input value was the literal
   concatenation of every prior test's typed text. Fixed with an explicit
   `afterEach(cleanup)` in `src/test/setup.js`.
2. **Concurrent `userEvent.type()` calls interleave keystrokes.** The first draft of
   `LoginPage.test.jsx`'s fill helper ran `user.type(email, ...)` and `user.type(password, ...)`
   concurrently via `Promise.all`. `userEvent.type()` simulates real focus + keystroke events;
   running two calls in parallel shuffled both strings' characters together into whichever field
   currently had focus — the captured request body was literally
   `{"email":"","password":"dSatvre0@negx!aPmapslse1.com"}` (email and password interleaved
   character-by-character). Fixed by awaiting each `type()` call sequentially.
3. **MSW handlers need to match the real absolute URL.** Relative-path handlers
   (`http.post('/api/auth/login', ...)`) silently never matched because the app's real
   `VITE_API_BASE_URL` (`http://localhost:8080`, from `.env.local`) makes axios build absolute
   URLs, while MSW's relative-path matching resolves against jsdom's default origin
   (`http://localhost:3000`). Fixed with a committed `frontend/.env.test` overriding
   `VITE_API_BASE_URL` to empty for Vitest's `test` mode — see ADR-037.

## Run
```
npm test
 Test Files  5 passed (5)
      Tests  16 passed (16)
```
`npm run build` and `npx oxlint src/` both clean afterward; no test files leak into `dist/`
(verified: `find dist -iname "*test*"` → empty).
