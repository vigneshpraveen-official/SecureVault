# Milestone 1 — Completion Checklist (M-14)

Session S1.6. Every line below was checked against the running application, not assumed.
PASS/GAP calls are honest — a gap found here got fixed this session (see "Gap fixes" at the
bottom); nothing is marked PASS without a pointer to real evidence in this folder.

## Project
| Item | Status | Evidence |
|---|---|---|
| Spring Boot runs | **PASS** | `s1.6-spring-boot-startup-log.txt` — clean startup, 5.3s |
| PostgreSQL connected | **PASS** | Same log — Flyway connects, Hikari pool starts |
| Schema finalised (Milestone 1 scope) | **PASS** | `users`+`credentials` per `V1__init.sql`; full 9-table target schema documented in `docs/db-design.md`, migrated table-by-table as later phases need them (by design, see ADR-003) |

## Authentication
| Item | Status | Evidence |
|---|---|---|
| Register API | **PASS** | `s1.6-journey-1-register.json` (201) |
| BCrypt hashing | **PASS** | `s1.1-db-password-hashes.txt` — `$2a$10$...`, two identical passwords → different hashes |
| Login verification (JWT) | **PASS** | `s1.6-journey-2-login.json` (200, JWT issued); wrong password → 401 (`s1.2-6-login-wrong-password-401.txt`) |
| JWT test matrix (M-19) | **PASS** | no token → 401, valid → 200, tampered → 401 (`s1.2-3-*`, `s1.2-4-*`, `s1.2-5-*`) |

## Vault
| Item | Status | Evidence |
|---|---|---|
| Create | **PASS** | `s1.6-journey-3-create.json` (201, no password field) |
| Read (single, decrypted) | **PASS** | `s1.6-journey-5-decrypt.json` — decrypts to the exact original password |
| Read (list, no passwords) | **PASS** | `s1.3-4-list-no-passwords.json` |
| Update (selective re-encryption) | **PASS** | title-only → ciphertext unchanged (`s1.4-3-*`); password change → ciphertext changes, decrypts to new value (`s1.4-5-*`, `s1.4-6-*`) |
| Delete | **PASS** | `s1.6-journey-7-delete.txt` (204) + `s1.6-journey-7b-verify-gone.txt` (404 after) |
| Ownership enforced on every op | **PASS** | cross-user 403 on get/update/delete (`s1.3-5-*`, `s1.4-9-*`, `s1.4-10-*`) |
| Search + category filter | **PASS** | case-insensitive partial match, no-match → 200 empty, per-user isolation (`s1.5-2-*` .. `s1.5-8-*`) |

## Security
| Item | Status | Evidence |
|---|---|---|
| BCrypt for account passwords | **PASS** | see Authentication row above |
| AES-256-GCM for vault secrets | **PASS** | `s1.6-journey-4-db-ciphertext.txt` — DB holds `base64(iv):base64(ciphertext)`, never plaintext |
| Secrets never in source/logs | **PASS** | `JWT_SECRET`/`AES_SECRET_KEY` are env-only, no defaults (verified S0.1); `AesEncryptionService`/`JwtService` never log input, output, or key |

## Quality
| Item | Status at start of S1.6 | Fix applied | Evidence |
|---|---|---|---|
| Validation | **GAP** — no Bean Validation existed; a blank/malformed request would either succeed with garbage data or fail as an unhandled exception | Added `@Valid` + `@NotBlank`/`@Email`/`@Size` on `UserRegisterRequest`, `LoginRequest`, `CredentialCreateRequest` (full coverage is still S2.2/M-25 — this is the honest minimum) | `s1.6-gap-fix-validation-400.txt` — blank/malformed register request → clean 400 with per-field messages |
| Exception handling | **GAP** — only specific business exceptions were handled per-controller; any unexpected exception (or a validation failure, before the fix above) would fall through to Spring's default error page, not the `ApiResponse` envelope | Added `common/exception/GlobalExceptionHandler` (`@RestControllerAdvice`) covering `MethodArgumentNotValidException` and a logged catch-all `Exception` handler. Full consolidation with per-controller handlers is still S2.3/M-26 | Same evidence — the 400 above already comes from this handler |
| Postman testing | **GAP** — collection existed but had no environment file and no saved example responses | Added `postman/SecureVault.postman_environment.json`; every one of the 19 requests now has a saved example response built from this session's real evidence | `postman/SecureVault.postman_collection.json`, `postman/SecureVault.postman_environment.json` |
| Clean project structure | **PASS** | Feature-first packages, one-line `package-info.java` per package, Spotless-enforced formatting (`mvn verify`) | repository layout itself |

## Full journey, one continuous run (register → login → create → encrypted in DB → decrypt → update → delete)
`s1.6-journey-1-register.json` → `s1.6-journey-2-login.json` → `s1.6-journey-3-create.json` →
`s1.6-journey-4-db-ciphertext.txt` → `s1.6-journey-5-decrypt.json` → `s1.6-journey-6-update.json`
+ `s1.6-journey-6b-verify-update.json` → `s1.6-journey-7-delete.txt` + `s1.6-journey-7b-verify-gone.txt`.

## Gap fixes applied this session
1. `@Valid` + minimal Bean Validation annotations on the three request DTOs that were still
   completely unvalidated (register, login, credential create).
2. `common/exception/GlobalExceptionHandler` — basic, not the full Phase 2 version — catches
   validation failures and any unexpected exception so every response, even an unplanned one,
   stays inside the `ApiResponse` envelope and never leaks a stack trace.
3. Postman environment file + saved example responses for all 19 requests.

No other gaps were found. Soft delete, MFA, refresh tokens, sharing, etc. are correctly *not*
expected at Milestone 1 — they're scheduled for later phases per `docs/roadmap.md`.

---
_Session S1.6 — 2026-08-11._
