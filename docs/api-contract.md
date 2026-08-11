# SecureVault — API Contract

Live index of every endpoint that actually exists in the codebase, grown one row at a time
as each session ships it. See `docs/securevault_master.md` §11 for the full target surface
and §9 for the response envelope contract. Never document an endpoint that isn't real yet.

Regenerated from the actual controllers in P2.4/S2.4 — every row below reflects the current
code, not what an earlier session's row said. Every error response, including 401/403 from
Spring Security itself, uses the same `ApiResponse` envelope (P2.3/M-27).

| Method | Path | Auth | Request DTO | Response DTO | Status codes | Error codes |
|---|---|---|---|---|---|---|
| GET | `/actuator/health` | none | — | `{ "status": "UP" }` | 200 | — |
| POST | `/api/auth/register` | none | `UserRegisterRequest` (fullName, email, password — full validation, see `docs/validation.md`) | `ApiResponse<UserResponse>` (id, fullName, email, role, createdAt — never passwordHash) | 201, 400, 409 | `VALIDATION_FAILED`, `DUPLICATE_EMAIL` |
| POST | `/api/auth/login` | none | `LoginRequest` (email, password — presence-only validation) | `ApiResponse<LoginResponse>` (accessToken, userId, fullName, email, role) | 200, 400, 401 | `VALIDATION_FAILED`, `INVALID_CREDENTIALS` |
| POST | `/api/vault` | JWT (Bearer) | `CredentialCreateRequest` (title, username, password, websiteUrl, notes, category) | `ApiResponse<CredentialResponse>` (id, title, username, websiteUrl, notes, category, favorite, createdAt, updatedAt — never password) | 201, 400, 401 | `VALIDATION_FAILED`, `INVALID_CREDENTIALS` |
| GET | `/api/vault/{id}` | JWT (Bearer) | — | `ApiResponse<CredentialDetailResponse>` (as `CredentialResponse`, plus decrypted `password`) — single-credential reveal only | 200, 400, 401, 403, 404 | `VALIDATION_FAILED`, `INVALID_CREDENTIALS`, `ACCESS_DENIED`, `CREDENTIAL_NOT_FOUND` |
| GET | `/api/vault` | JWT (Bearer) | — optional `?category=` filter | `ApiResponse<List<CredentialSummaryResponse>>` — list-view shape, distinct type from `CredentialResponse`, no passwords | 200, 401 | `INVALID_CREDENTIALS` |
| GET | `/api/vault/search?q=` | JWT (Bearer) | — `q` required | `ApiResponse<List<CredentialSummaryResponse>>` — case-insensitive partial match on title/username/websiteUrl | 200, 400, 401 | `VALIDATION_FAILED`, `INVALID_CREDENTIALS` |
| PUT | `/api/vault/{id}` | JWT (Bearer) | `CredentialUpdateRequest` (all fields optional; null = unchanged; non-null values still validated) | `ApiResponse<CredentialResponse>` | 200, 400, 401, 403, 404 | `VALIDATION_FAILED`, `INVALID_CREDENTIALS`, `ACCESS_DENIED`, `CREDENTIAL_NOT_FOUND` |
| DELETE | `/api/vault/{id}` | JWT (Bearer) | — | none — the sole endpoint exempt from the `ApiResponse` envelope (HTTP 204 forbids a body, RFC 9110 §15.3.5); hard delete for now, soft delete arrives in S4.3 | 204, 401, 403, 404 | `INVALID_CREDENTIALS`, `ACCESS_DENIED`, `CREDENTIAL_NOT_FOUND` |

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
enum/JSON body, a path variable of the wrong type, or a missing required query parameter — see
`GlobalExceptionHandler`); every other error code carries `errors: null` and puts the detail in
`message` instead.

## Full `ErrorCode` set (master §9)

`USER_NOT_FOUND` · `DUPLICATE_EMAIL` · `INVALID_CREDENTIALS` · `CREDENTIAL_NOT_FOUND` ·
`VALIDATION_FAILED` · `ACCESS_DENIED` · `PASSWORD_REUSED` · `SHARE_ALREADY_EXISTS` ·
`SELF_SHARE_NOT_ALLOWED` · `TOKEN_EXPIRED` · `TOKEN_INVALID` · `MFA_REQUIRED` · `MFA_INVALID` ·
`INTERNAL_ERROR`. Only the ones actually reachable by Phase 1-2 code appear in the table above;
the rest are reserved for the phases that introduce them (sharing, refresh tokens, MFA).

---
_Last updated: S2.4 — 2026-08-11._
