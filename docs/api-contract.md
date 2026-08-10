# SecureVault — API Contract

Live index of every endpoint that actually exists in the codebase, grown one row at a time
as each session ships it. See `docs/securevault_master.md` §11 for the full target surface
and §9 for the response envelope contract. Never document an endpoint that isn't real yet.

| Method | Path | Auth | Request DTO | Response DTO | Status codes | Error codes |
|---|---|---|---|---|---|---|
| GET | `/actuator/health` | none | — | `{ "status": "UP" }` | 200 | — |
| POST | `/api/auth/register` | none | `UserRegisterRequest` (fullName, email, password) | `ApiResponse<UserResponse>` (id, fullName, email, role, createdAt — never passwordHash) | 201, 409 | `DUPLICATE_EMAIL` |
| POST | `/api/auth/login` | none | `LoginRequest` (email, password) | `ApiResponse<LoginResponse>` (accessToken, userId, fullName, email, role) | 200, 401 | `INVALID_CREDENTIALS` |
| POST | `/api/vault` | JWT (Bearer) | `CredentialCreateRequest` (title, username, password, websiteUrl, notes) | `ApiResponse<CredentialResponse>` (id, title, username, websiteUrl, notes, category, favorite, createdAt, updatedAt — never password) | 201 | — |
| GET | `/api/vault/{id}` | JWT (Bearer) | — | `ApiResponse<CredentialDetailResponse>` (as above, plus decrypted `password`) — single-credential reveal only | 200, 403, 404 | `ACCESS_DENIED`, `CREDENTIAL_NOT_FOUND` |
| GET | `/api/vault` | JWT (Bearer) | — | `ApiResponse<List<CredentialResponse>>` — no passwords; optional `?category=` filter | 200, 401 | — |
| GET | `/api/vault/search?q=` | JWT (Bearer) | — | `ApiResponse<List<CredentialResponse>>` — case-insensitive partial match on title/username/websiteUrl | 200, 401 | — |
| PUT | `/api/vault/{id}` | JWT (Bearer) | `CredentialUpdateRequest` (all fields optional; null = unchanged) | `ApiResponse<CredentialResponse>` | 200, 403, 404 | `ACCESS_DENIED`, `CREDENTIAL_NOT_FOUND` |
| DELETE | `/api/vault/{id}` | JWT (Bearer) | — | none (hard delete for now — soft delete in S4.3) | 204, 403, 404 | `ACCESS_DENIED`, `CREDENTIAL_NOT_FOUND` |

---
_Last updated: S1.5 — 2026-08-11._
