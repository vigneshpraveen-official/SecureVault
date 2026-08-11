# SecureVault — API Contract

Live index of every endpoint that actually exists in the codebase, grown one row at a time
as each session ships it. See `docs/securevault_master.md` §11 for the full target surface
and §9 for the response envelope contract. Never document an endpoint that isn't real yet.

Regenerated in P4.5/S4.5 — every row below reflects the current code, not what an earlier
session's row said. Every error response, including 401/403 from Spring Security itself, uses
the same `ApiResponse` envelope (P2.3/M-27).

| Method | Path | Auth | Request DTO | Response DTO | Status codes | Error codes |
|---|---|---|---|---|---|---|
| GET | `/actuator/health` | none | — | `{ "status": "UP" }` | 200 | — |
| POST | `/api/auth/register` | none | `UserRegisterRequest` (fullName, email, password — full validation, see `docs/validation.md`) | `ApiResponse<UserResponse>` (id, fullName, email, role, createdAt — never passwordHash) | 201, 400, 409 | `VALIDATION_FAILED`, `DUPLICATE_EMAIL` |
| POST | `/api/auth/login` | none | `LoginRequest` (email, password — presence-only validation) | `ApiResponse<LoginResponse>` (accessToken, userId, fullName, email, role) | 200, 400, 401 | `VALIDATION_FAILED`, `INVALID_CREDENTIALS` |
| POST | `/api/password/strength` | none (public utility) | `PasswordStrengthRequest` (password — `@NotBlank` only, deliberately unrestricted) | `ApiResponse<PasswordStrengthResponse>` (score 0-5, strength label, entropyBits, feedback[]) — never logs/persists the password (docs/password-policy.md) | 200, 400 | `VALIDATION_FAILED` |
| POST | `/api/password/generate` | none (public utility) | `GenerateRequest` (length 8-128, includeUppercase/Lowercase/Numbers/Symbols, excludeAmbiguous — at least one class required) | `ApiResponse<GenerateResponse>` (password, strength — reuses the strength endpoint's scoring) | 200, 400 | `VALIDATION_FAILED` |
| POST | `/api/vault` | JWT (Bearer) | `CredentialCreateRequest` (title, username, password, websiteUrl, notes, category) | `ApiResponse<CredentialResponse>` (id, title, username, websiteUrl, notes, category, favorite, strengthScore, createdAt, updatedAt — never password) | 201, 400, 401 | `VALIDATION_FAILED`, `INVALID_CREDENTIALS` |
| GET | `/api/vault/{id}` | JWT (Bearer) | — | `ApiResponse<CredentialDetailResponse>` (as `CredentialResponse` minus strengthScore, plus decrypted `password`) — single-credential reveal only; excludes soft-deleted credentials (404) | 200, 400, 401, 403, 404 | `VALIDATION_FAILED`, `INVALID_CREDENTIALS`, `ACCESS_DENIED`, `CREDENTIAL_NOT_FOUND` |
| **GET** | **`/api/vault`** | JWT (Bearer) | query params below | `ApiResponse<PagedResponse<CredentialSummaryResponse>>` — paginated, sorted, filtered (P4.5/M-34) | 200, 400, 401 | `VALIDATION_FAILED`, `INVALID_CREDENTIALS` |
| GET | `/api/vault/search?q=` | JWT (Bearer) | — `q` required | `ApiResponse<List<CredentialSummaryResponse>>` — case-insensitive **OR** partial match across title/username/websiteUrl (contrast with the AND-combined per-field filters on `GET /api/vault` above) | 200, 400, 401 | `VALIDATION_FAILED`, `INVALID_CREDENTIALS` |
| GET | `/api/vault/health` | JWT (Bearer) | — | `ApiResponse<VaultHealthResponse>` (totalCredentials, band counts, reusedPasswordCount, staleCredentialCount, healthScore 0-100 — formula in `docs/password-policy.md` §3) — aggregate only, never a password or hash | 200, 401 | `INVALID_CREDENTIALS` |
| GET | `/api/vault/trash` | JWT (Bearer) | — | `ApiResponse<List<CredentialSummaryResponse>>` — only the caller's soft-deleted credentials (P4.3/M-38) | 200, 401 | `INVALID_CREDENTIALS` |
| GET | `/api/vault/{id}/history` | JWT (Bearer) | — | `ApiResponse<List<PasswordHistoryVersionResponse>>` (version, createdAt) — **never** a historical password, not even to the owner (P4.2/M-35) | 200, 400, 401, 403, 404 | `VALIDATION_FAILED`, `INVALID_CREDENTIALS`, `ACCESS_DENIED`, `CREDENTIAL_NOT_FOUND` |
| PUT | `/api/vault/{id}` | JWT (Bearer) | `CredentialUpdateRequest` (all fields optional; null = unchanged; non-null values still validated) | `ApiResponse<CredentialResponse>` — rejects password reuse against the last 5 versions (P4.2/M-36) | 200, 400, 401, 403, 404, 409 | `VALIDATION_FAILED`, `INVALID_CREDENTIALS`, `ACCESS_DENIED`, `CREDENTIAL_NOT_FOUND`, `PASSWORD_REUSED` |
| PUT | `/api/vault/{id}/restore` | JWT (Bearer) | — | `ApiResponse<CredentialResponse>` — no-op (200, unchanged state) if the credential is already active, not a 409 (ADR-018) | 200, 401, 403, 404 | `INVALID_CREDENTIALS`, `ACCESS_DENIED`, `CREDENTIAL_NOT_FOUND` |
| DELETE | `/api/vault/{id}` | JWT (Bearer) | — | none — the sole endpoint exempt from the `ApiResponse` envelope (HTTP 204 forbids a body, RFC 9110 §15.3.5). **Soft delete as of P4.3** — sets `deleted=true`, moves the credential to the trash, does not remove the row | 204, 401, 403, 404 | `INVALID_CREDENTIALS`, `ACCESS_DENIED`, `CREDENTIAL_NOT_FOUND` |
| DELETE | `/api/vault/{id}/permanent` | JWT (Bearer) | — | none (204) — hard-deletes the credential AND its password history in one transaction; only operates on an already-trashed credential (404 otherwise); audit logs are never touched (P4.3/M-39) | 204, 401, 403, 404 | `INVALID_CREDENTIALS`, `ACCESS_DENIED`, `CREDENTIAL_NOT_FOUND` |
| POST | `/api/vault/recompute-strength` | JWT (Bearer) | — | `ApiResponse<Void>` — fire-and-forget; the recompute runs off the request thread (P4.6/M-40), so 202 means "started," not "done" | 202, 401 | `INVALID_CREDENTIALS` |

## `GET /api/vault` query parameters (P4.5/M-34)

All optional and freely combinable; owner and `deleted=false` are always ANDed in regardless of
what's requested.

| Param | Type | Default | Notes |
|---|---|---|---|
| `page` | int | `0` | Zero-indexed. A page past the last returns an empty `content[]` with correct totals — not an error. |
| `size` | int | `20` | Capped at **100** (`400 VALIDATION_FAILED` above that) — an unbounded page size is a self-inflicted denial of service. |
| `sortBy` | string | `createdAt` | Whitelisted against real `Credential` fields: `title`, `username`, `websiteUrl`, `category`, `favorite`, `strengthScore`, `createdAt`, `updatedAt`. Anything else is `400 VALIDATION_FAILED` with the exact allowed list in the message — an unvalidated sort field is both a 500 waiting to happen (an invalid JPA property path) and a schema leak. |
| `direction` | string | `desc` | `asc` or `desc`, case-insensitive. |
| `category` | enum | — | Exact match. |
| `title` | string | — | Case-insensitive partial match. |
| `username` | string | — | Case-insensitive partial match. |
| `website` | string | — | Case-insensitive partial match against `websiteUrl`. |

Example: `/api/vault?page=0&size=10&sortBy=title&direction=asc&category=DEVELOPMENT`

## Error responses, uniformly

Every non-2xx response — including 401/403 raised by Spring Security before a controller ever
runs, via the custom `AuthenticationEntryPoint`/`AccessDeniedHandler` in `SecurityConfig` — has
this shape:

```json
{
  "success": false,
  "message": "human-readable message",
  "data": null,
  "errorCode": "ONE_OF_THE_FIXED_CODES",
  "errors": [{ "field": "title", "message": "must not be blank" }],
  "timestamp": "2026-08-11T00:00:00Z"
}
```

`errors` is only populated for `VALIDATION_FAILED` (Bean Validation failures, a malformed
enum/JSON body, a path variable of the wrong type, a missing required query parameter, or an
out-of-range/unwhitelisted query parameter like `size`/`sortBy` — see `GlobalExceptionHandler`);
every other error code carries `errors: null` and puts the detail in `message` instead.

## Full `ErrorCode` set (master §9)

`USER_NOT_FOUND` · `DUPLICATE_EMAIL` · `INVALID_CREDENTIALS` · `CREDENTIAL_NOT_FOUND` ·
`VALIDATION_FAILED` · `ACCESS_DENIED` · `PASSWORD_REUSED` · `SHARE_ALREADY_EXISTS` ·
`SELF_SHARE_NOT_ALLOWED` · `TOKEN_EXPIRED` · `TOKEN_INVALID` · `MFA_REQUIRED` · `MFA_INVALID` ·
`INTERNAL_ERROR`. `PASSWORD_REUSED` is now live (P4.2); the rest reserved for sharing/refresh
tokens/MFA (Phase 5).

---
_Last updated: S4.6 — 2026-08-11._
