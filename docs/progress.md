# SecureVault — Progress Log

## CURRENT STATE
- Phase: 5 — Sharing, sessions, platform hardening (**complete**) — Milestone 3 in progress (Phases 5-6; Phase 6 frontend still ahead)
- Last session: S5.8 (OpenAPI documentation and admin module)
- Build: green | Tests: 13 passing (unchanged — Phase 5 verification is entirely live curl/psql/Redis/MailHog/Swagger evidence, same pattern as every prior phase; JUnit+Testcontainers integration tests arrive in Phase 7, D-16) | Migrations applied: V0-V4 (Phases 1-4), V5 (`credential_shares`), V6 (`refresh_tokens`), V7 (`mfa_and_devices` + `refresh_tokens.device_fingerprint`), V8 (`login_attempts_and_alerts`), V9 (`notifications`)
- Working branch: main (personal fork repo only; no central-repo remote configured yet — see ADR-006)
- Next session: S6.1 — Frontend scaffold (Vite, Tailwind, router, axios interceptors, Redux store)
- Open blockers: ERD PNG export is still a manual dbdiagram.io step (DBML source not yet regenerated for Phase 5's 7 new tables either). S9.1 (central-repo push) remains blocked pending mentor push/branch instructions (ADR-006). Swagger-disabled-in-prod (application-prod.yml) and Neon/Upstash-specific settings are declared but not live-verified — no prod infra exists yet (Phase 8).
- Commit cadence: **one commit per phase**, not per session (ADR-008) — Phase 5's eight sessions land in a single commit
- Full phase/milestone tracker: `docs/roadmap.md` (32/53 sessions done, Milestone 1 complete, Phase 2-4 complete, Milestone 2 complete, **Phase 5 complete**)

## NEXT UP
1. S6.1 — Frontend scaffold (Vite, Tailwind, router, axios interceptors, Redux store)
2. S6.2 — Auth screens + protected routes + MFA
3. S6.3 — Vault UI (list, search, filter, pagination, CRUD, reveal/copy)

---
## SESSION LOG

### S0.1 — 2026-08-10 — Workspace bootstrap
**Mentor tasks:** M-03 (PostgreSQL installed, `securevault` database created, no tables yet)
**Done:**
- Initialized git repo; remote `origin` set to the personal fork (github.com/vigneshpraveen-official/SecureVault). No `central` remote yet — the mentor has not yet given push/merge instructions for the upstream repo (see ADR-006).
- Full directory structure per master §8 created, including empty `frontend/` with `.gitkeep`.
- `.gitignore`, `.env.example` (empty placeholders, all vars from master §13), and a real local-only `.env` with two freshly generated secrets (`JWT_SECRET`, `AES_SECRET_KEY`, both `openssl rand -base64 32`). Confirmed gitignored via `git check-ignore -v .env`.
- `docker-compose.yml`: postgres:16-alpine, redis:7-alpine, mailhog — all healthy. Verified `SELECT version()` returns PostgreSQL 16.14 and `\dt` shows no tables.
- Spring Boot backend skeleton at `backend/`: Maven, `com.securevault`, Java 21, **Spring Boot 3.5.16** (see ADR-007 — D-02 said 3.3.x, which is now fully EOL), all Step-4 dependencies, 14 feature packages each with a one-line `package-info.java`.
- `application.yml` (shared, `ddl-auto=validate`, Flyway on, no defaults for `JWT_SECRET`/`AES_SECRET_KEY`), `application-local.yml` (gitignored, real local datasource) + committed `application-local.yml.example`, `application-prod.yml` placeholder.
- `db/migration/V0__baseline.sql` — no-op migration so Flyway has history and `ddl-auto=validate` has zero entities to validate against (chosen over disabling validation — keeps D-04 honest from day 1). Real schema arrives as `V1__init.sql` in S0.3.
- `.editorconfig` (4-space Java/SQL/shell, 2-space JSON/YAML/JS, LF, trim trailing whitespace) + Spotless (Maven, Google Java Format **AOSP** style to match 4-space, bound to `verify`). `.gitattributes` added (`* text=auto eol=lf`) since `core.autocrlf=true` was set globally and would otherwise silently reintroduce CRLF on checkout.
- Full docs system (`progress.md`, `guide.md`, `decisions.md`, `api-contract.md`, `db-design.md`) and AI consistency layer (`docs/ai/CONTEXT.md`, `docs/ai/CONVENTIONS.md`, root `CLAUDE.md`/`AGENTS.md`/`GEMINI.md`, `scripts/context-pack.sh`, `scripts/sync-submission.sh`).
**Files:** see the full repo — this is the initial scaffold commit.
**Decisions:** ADR-001..ADR-007 in `docs/decisions.md` (Java 21 + Spring Boot 3.5.16, monolith, Flyway+validate, AES-256-GCM, AOSP Java formatting via Spotless, V0 no-op baseline strategy, fork-only single-repo workflow for now).
**Verified:**
- `docker compose ps` — postgres, redis, mailhog all healthy.
- `SELECT version();` → PostgreSQL 16.14; `\dt` → no relations before the backend's first run. After `mvn spring-boot:run` applied `V0__baseline.sql`, `\dt` shows only Flyway's own `flyway_schema_history` bookkeeping table — zero application tables, as M-03 requires.
- `mvn clean compile` → success. `mvn spotless:apply` → reformats cleanly, `verify`-bound `spotless:check` will fail the build on drift.
- `mvn spring-boot:run` (profile `local`, env from `.env`) → started in 28.8s, Flyway applied V0, Hikari connected, `GET /actuator/health` → `{"status":"UP"}`.
- `git check-ignore -v .env` → matched, confirmed not staged.
**Blockers:** none.
**Commit:** `chore(workspace): bootstrap SecureVault workspace`

**Addendum (same session, pre-S0.2):** added `docs/roadmap.md` — a full 53-session phase/milestone
checklist tracker (checkboxes ticked at each Session Close going forward), wired into
`docs/progress.md` CURRENT STATE, `docs/ai/CONTEXT.md`, and `docs/guide.md`'s new
"Daily workflow" section, at the developer's request to keep the whole project's progress
visible at a glance across daily/ad-hoc sessions and across AI tools.

### S0.2 — 2026-08-11 — Product decomposition + architecture reasoning
**Mentor tasks:** M-01, M-02 — written deliverables, no code.
**Done:**
- `docs/decomposition.md` — 41 features (Feature / Why / Priority / Milestone table) covering
  auth, MFA, sessions, vault CRUD, categorisation, search, favourites, notes, generation,
  strength analysis, history, sharing, permissions, audit, monitoring, notifications,
  dashboards, reports, admin, deployment. Closes with "Features we are deliberately NOT
  building," citing master §3 Track C item by item.
- `docs/architecture.md` — reasoned (not looked-up) answers to where JWT is created/validated,
  where encryption happens and why not DB/browser, what Redis holds, why audit logs share the
  write transaction, why email is async; an ASCII layer diagram; and a "why modular monolith,
  not microservices" section.
- `docs/guide.md` — Architecture overview section now points to `docs/architecture.md` for the
  full reasoned diagram.
**Files:** `docs/decomposition.md`, `docs/architecture.md`, `docs/guide.md`, `docs/progress.md`, `docs/roadmap.md`.
**Decisions:** none — no locked decision touched; no ADR needed this session.
**Verified:** no code changed, so no build/test impact; content checked against master §3, §5 (M-01/M-02), §9 (layering), §10/§11 (schema/API surface) for internal consistency — nothing invented that contradicts the spec.
**Blockers:** none.
**Commit:** `docs(planning): add product decomposition and architecture reasoning (M-01, M-02)`

### S0.3 — 2026-08-11 — Schema design, ERD, Flyway baseline
**Mentor tasks:** M-03, M-04, M-05
**Done:**
- Verified `securevault-postgres` (Docker, from S0.1) reachable at `localhost:5432` / db `securevault`.
- `docs/db-design.md` rewritten from stub to full target schema (all 9 tables from master §10):
  column/type/constraints/notes per table, PK/FK/unique/index callouts, an "Index rationale"
  section answering M-23 in advance (title, category, composite `(user_id, deleted)` — why
  each exists, the write-cost tradeoff, which queries each serves), the relationship summary,
  and exact dbdiagram.io export steps.
- `docs/erd/securevault.dbml` — full DBML source, all 9 tables and relations, with inline
  notes on which phase/session adds each non-Phase-1 table.
- `backend/src/main/resources/db/migration/V1__init.sql` — **only** `users` and `credentials`
  (Phase 1 scope), BIGSERIAL PKs, TIMESTAMPTZ, snake_case, `encrypted_password` column name,
  composite index `(user_id, deleted)`, single-column indexes on `title` and `category`.
- No JPA entities added (deliberately deferred to Phase 1, per the session's own constraint).
**Files:** `docs/db-design.md`, `docs/erd/securevault.dbml`, `backend/src/main/resources/db/migration/V1__init.sql`, `docs/guide.md`, `docs/progress.md`, `docs/roadmap.md`.
**Decisions:** none new — column types not pinned by master §10 (e.g. `ip_address`/`user_agent`/`token_hash` lengths on the not-yet-migrated tables) are flagged inline in `db-design.md` as this session's own reasonable choices, revisitable when each table is actually migrated. Not ADR-worthy on their own.
**Verified:**
- `mvn spring-boot:run` (profile `local`) → Flyway log: `Successfully validated 2 migrations`, `Migrating schema "public" to version "1 - init"`, `now at version v1`. `GET /actuator/health` → `{"status":"UP"}`.
- `\dt` → `users`, `credentials`, `flyway_schema_history`. `\d credentials` / `\d users` → every column, the FK, and all three indexes present exactly as specified in `V1__init.sql`.
**Blockers:** ERD PNG export from dbdiagram.io is a manual step the developer still needs to do (I can't reach that web UI) — DBML source is committed and ready to paste in.
**Commit:** `feat(db): add Phase 1 schema (users, credentials) with Flyway V1 and full ERD docs`

### S1.1 — 2026-08-11 — User entity, registration, BCrypt
**Mentor tasks:** M-06, M-07
**Done:**
- `User` entity (`user/User.java`) matching `V1__init.sql`'s `users` table exactly; `Role` enum (`USER`/`TEAM_MEMBER`/`ADMIN`, default `USER`); timestamps via Hibernate `@CreationTimestamp`/`@UpdateTimestamp` (not full JPA auditing — simpler for two columns, no `createdBy`/`modifiedBy` need yet).
- `UserRepository` (`existsByEmail`, `findByEmail`), `UserService`/`UserServiceImpl.register(...)` — rejects duplicate email via `DuplicateEmailException` (plain exception, formalized in S2.3), hashes with `BCryptPasswordEncoder(10)`.
- `UserRegisterRequest` / `UserResponse` DTOs — response never carries `passwordHash`.
- `common/response/ApiResponse<T>` — the real envelope (§9/D-11), used from this session on.
- `UserController` — `POST /api/auth/register`, plus a local `@ExceptionHandler` for `DuplicateEmailException` → 409 (`DUPLICATE_EMAIL`). Full `@ControllerAdvice` arrives in S2.3.
- `config/SecurityConfig` — `PasswordEncoder` bean + a permit-all filter chain with a `TODO(S1.2)` marking where the real JWT chain replaces it.
- Added `Auth` folder to `postman/SecureVault.postman_collection.json` (register success + duplicate-email requests); `docs/api-contract.md` updated.
**Files:** `user/*`, `user/dto/*`, `common/response/ApiResponse.java`, `config/SecurityConfig.java`, `postman/SecureVault.postman_collection.json`, `docs/api-contract.md`, `docs/evidence/milestone-1/s1.1-*`.
**Decisions:** none new beyond the timestamp-strategy note above (not ADR-worthy on its own — matches the pattern already set for `credentials` in ADR-004).
**Verified (evidence in `docs/evidence/milestone-1/`, curl used in place of a Postman GUI — Postman requests are in the collection for manual re-run):**
- `POST /api/auth/register` (Alice) → 201, body has no `passwordHash` (`s1.1-register-success-alice.json`).
- `POST /api/auth/register` (Bob, same password as Alice) → 201 (`s1.1-register-success-bob.json`).
- `POST /api/auth/register` (Alice's email again) → 409, `errorCode: DUPLICATE_EMAIL` (`s1.1-duplicate-email-409.txt`).
- `SELECT id, email, password_hash, role FROM users;` → both rows `$2a$10$...`, and **the two hashes for the identical password are different** — BCrypt embeds a fresh random salt in every hash it generates, so equal input never produces equal output (`s1.1-db-password-hashes.txt`).
**Blockers:** none.
**Commit:** _batched — see Phase 1 close, ADR-008._

### S1.2 — 2026-08-11 — Spring Security, JWT, login
**Mentor tasks:** M-15, M-16, M-17, M-18, M-19
**Done:**
- `security/JwtService` — jjwt **0.12.7** (new dependency, ADR-009), `generateAccessToken`, `extractUsername`, `isTokenValid`, `isTokenExpired`. Secret/expiry from `app.security.jwt-secret`/`jwt-access-expiry-ms` (env-backed, no defaults) — never hardcoded.
- `security/JwtAuthenticationFilter` (`OncePerRequestFilter`) — reads `Authorization: Bearer `, validates, sets `SecurityContext`; malformed/expired/tampered tokens *and* tokens for a since-deleted user are caught and result in an unauthenticated request, never a 500.
- `security/CustomUserDetailsService` (loads by email) + `security/UserPrincipal` (wraps `User`, role → `ROLE_<ROLE>` authority).
- `config/SecurityConfig` rewritten (replaces S1.1's permit-all placeholder): CSRF/form-login/httpBasic disabled, `SessionCreationPolicy.STATELESS`, `permitAll` on `/api/auth/**` + `/actuator/health` + swagger paths, `authenticated()` everywhere else, `JwtAuthenticationFilter` registered before `UsernamePasswordAuthenticationFilter`, CORS driven by `APP_CORS_ORIGINS`. **Bug caught during verification:** Spring Security's default `AuthenticationEntryPoint` returns 403 for unauthenticated requests once form-login/httpBasic are disabled — added an explicit entry point returning 401, since M-19 requires 401, not 403.
- `security/AuthController` — `POST /api/auth/login`, `AuthenticationManager` + BCrypt verification via `DaoAuthenticationProvider`, generic `INVALID_CREDENTIALS` message on any failure (never reveals which field was wrong).
- `vault/VaultController` — temporary stub (`GET /api/vault` → empty list), authenticated-only, exists solely to prove the filter chain; replaced by the real endpoint in S1.3.
- Postman collection: `Auth` folder gets login (success + wrong-password) requests with a test script that captures `accessToken` into a collection variable; new `Vault` folder (no-token / valid-token / tampered-token). `docs/api-contract.md` updated.
**Files:** `security/*`, `security/dto/*`, `config/SecurityConfig.java`, `vault/VaultController.java`, `backend/pom.xml`, `docs/decisions.md` (ADR-009), `postman/SecureVault.postman_collection.json`, `docs/api-contract.md`, `docs/evidence/milestone-1/s1.2-*`.
**Decisions:** ADR-009 (jjwt 0.12.7 dependency). SecurityConfig stays in `config/` (per master §8's directory tree) even though the P1.2 prompt text groups it under "Implement in com.securevault.security" — master's canonical layout wins over session-prompt phrasing.
**Verified (evidence in `docs/evidence/milestone-1/`, curl in place of a Postman GUI):**
- Register → 201 (`s1.2-1-register-success.json`).
- Login → 200 with a JWT (`s1.2-2-login-jwt.json`).
- `GET /api/vault` no token → **401** (`s1.2-3-vault-no-token-401.txt`).
- `GET /api/vault` valid token → **200**, empty list (`s1.2-4-vault-valid-token-200.txt`).
- `GET /api/vault` tampered token (valid JWT + trailing garbage) → **401** (`s1.2-5-vault-tampered-token-401.txt`).
- Bonus: login with wrong password → 401, generic message, no field disclosed (`s1.2-6-login-wrong-password-401.txt`).
**Blockers:** none.
**Commit:** _batched — see Phase 1 close, ADR-008._

### S1.3 — 2026-08-11 — Credential entity, AES-GCM, create/read
**Mentor tasks:** M-08, M-09, M-10
**Done:**
- `security/crypto/AesEncryptionService` — AES-256-GCM (`AES/GCM/NoPadding`), 128-bit tag, fresh `SecureRandom` 12-byte IV per call, `encrypt`/`decrypt` in the `base64(iv):base64(ciphertext)` format (ADR-004). Constructor decodes and length-checks `AES_SECRET_KEY`, failing the whole app context at startup on a missing/malformed/wrong-length key (ADR-010). Unit test proves two encryptions of the same plaintext differ and both decrypt correctly.
- `vault/Credential` entity + `vault/Category` enum (7 values, default `OTHER` — filter API is S1.5). Matches `V1__init.sql` exactly; no new migration needed.
- `vault/CredentialRepository`, `vault/CredentialService`/`Impl` — owner resolved from the JWT principal (never the request body), create encrypts before persisting, get-by-id checks ownership before decrypting (404 if missing, 403 if not the owner), list never decrypts.
- `vault/CredentialController` — `POST /api/vault`, `GET /api/vault/{id}`, `GET /api/vault` — **replaces** S1.2's stub `VaultController` (deleted).
- `CredentialResponse` (no password, create+list) vs `CredentialDetailResponse` (decrypted password, single-reveal only) — two distinct DTOs so the "never in a list" rule is a type-level guarantee, not a runtime check.
- Postman `Vault` folder gets create + reveal + cross-user-403 requests; `docs/api-contract.md` updated.
**Files:** `security/crypto/AesEncryptionService.java` (+test), `vault/Credential.java`, `vault/Category.java`, `vault/CredentialRepository.java`, `vault/CredentialService.java`, `vault/CredentialServiceImpl.java`, `vault/CredentialController.java`, `vault/dto/*`, `vault/CredentialNotFoundException.java`, `vault/CredentialAccessDeniedException.java` (deleted `vault/VaultController.java`), `docs/decisions.md` (ADR-010), `postman/SecureVault.postman_collection.json`, `docs/api-contract.md`, `docs/evidence/milestone-1/s1.3-*`.
**Decisions:** ADR-010 (AES implementation details — see above). 404 vs 403 split: nonexistent ID → `CREDENTIAL_NOT_FOUND` (404), exists but wrong owner → `ACCESS_DENIED` (403) — matches master §12's ownership-check-first model and the distinct error codes already defined in §9.
**Verified (evidence in `docs/evidence/milestone-1/`):**
- `mvn test` → `AesEncryptionServiceTest` green (same plaintext → different ciphertext, both decrypt correctly; wrong key length fails fast).
- Two credentials created for one user (Dave) → 201 each, no `password` field (`s1.3-1-*`, `s1.3-2-*`).
- `GET /api/vault/1` (owner) → 200, `password` field matches the original plaintext exactly (`s1.3-3-*`).
- `GET /api/vault` (owner) → both credentials, neither has a `password` field (`s1.3-4-*`).
- `GET /api/vault/1` as a second user (Erin, not the owner) → **403** (`s1.3-5-*`).
- `SELECT ... FROM credentials` → `encrypted_password` column holds `base64(iv):base64(ciphertext)` for both rows, no plaintext anywhere in the database (`s1.3-6-*`).
**Blockers:** none.
**Commit:** _batched — see Phase 1 close, ADR-008._

### S1.4 — 2026-08-11 — Update, delete, ownership verification
**Mentor tasks:** M-11, M-12, M-13
**Done:**
- `vault/dto/CredentialUpdateRequest` — every field optional; `null` means "leave unchanged" (title, username, websiteUrl, notes, category all follow this rule).
- `CredentialServiceImpl.update(...)` — password re-encryption logic: **decrypt-and-compare**, not presence-based. If `password` is `null` → skip entirely. If present, decrypt the *current* stored ciphertext and compare the resulting plaintext to the incoming value; only re-encrypt (and only then does the stored ciphertext change) if they differ. Chosen over "treat any present value as a change" because GCM's random-IV-per-encryption (D-05) means ciphertext is never comparable directly — the only correct way to know whether the password *actually* changed is a plaintext-to-plaintext comparison, which requires decrypting the existing value first.
- `CredentialServiceImpl.delete(...)` — **hard delete for now**, explicit code comment + this log entry marking it as an intentional simplification, not an oversight — soft delete (`deleted`/`deletedAt`) replaces this in S4.3 (M-37..M-39).
- Ownership check reused, not duplicated: `getByIdForUser`, `update`, and `delete` all go through the same private `loadOwned(id, userId)` — 404 if the row doesn't exist, 403 if it exists but isn't the caller's. No endpoint accepts a `userId` from the client; it always comes from `@AuthenticationPrincipal`.
- `CredentialController` — `PUT /api/vault/{id}`, `DELETE /api/vault/{id}` (204, no body — HTTP forbids a body on 204, so it's the one endpoint shape exempt from the `ApiResponse` envelope).
- Postman `Vault` folder gets update (title-only, password, cross-user-403) and delete (success, cross-user-403) requests; `docs/api-contract.md` updated.
**Files:** `vault/dto/CredentialUpdateRequest.java`, `vault/CredentialService.java`, `vault/CredentialServiceImpl.java`, `vault/CredentialController.java`, `postman/SecureVault.postman_collection.json`, `docs/api-contract.md`, `docs/evidence/milestone-1/s1.4-*`.
**Decisions:** none new — the decrypt-and-compare choice is a session-scoped implementation detail explained above and in the code comment, not a locked-decision-level ADR.
**Verified (evidence in `docs/evidence/milestone-1/`):**
- Update title only → DB `encrypted_password` for the row is **byte-for-byte identical** before/after (`s1.4-1-*`, `s1.4-3-*`).
- Update password → DB ciphertext **changes**, and `GET /api/vault/{id}` decrypts to the new plaintext exactly (`s1.4-4-*`, `s1.4-5-*`, `s1.4-6-*`).
- Delete 1 of Dave's 3 credentials → 204, remaining list shows exactly the other two, by id (`s1.4-7-*`, `s1.4-8-*`).
- Erin (not the owner) attempts `PUT` and `DELETE` on Dave's credential → **403** both times, and a follow-up `GET` proves Dave's row is completely untouched (`s1.4-9-*`, `s1.4-10-*`, `s1.4-11-*`).
**Blockers:** none.
**Commit:** _batched — see Phase 1 close, ADR-008._

### S1.5 — 2026-08-11 — Category, search, filtering, indexes
**Mentor tasks:** M-20, M-21, M-22, M-23
**Done:**
- `Category` confirmed `@Enumerated(EnumType.STRING)` (already true since S1.3) — ADR-011 explains why `ORDINAL` is a silent-corruption risk. `CredentialCreateRequest` gains an optional `category` field; service defaults to `OTHER` when absent (required at the entity/DB level, sensible default at the API level).
- `CredentialRepository.search(...)` — **`@Query` (JPQL)**, chosen over a Spring Data derived name: a derived name for "one AND (three OR'd, case-insensitive, partial-match fields)" would need `userId` repeated in every OR clause (the method-name grammar has no grouping) — long and easy to get subtly wrong. JPQL makes the boolean structure explicit and reviewable.
- `GET /api/vault/search?q=` — case-insensitive partial match across `title`/`username`/`websiteUrl`, scoped to the authenticated user.
- `GET /api/vault?category=` — `CredentialService.listForUser(userId, Category)` takes a **nullable** category (null = no filter). Chosen so S4.5's dynamic-filter rewrite (Specifications, D-12) can absorb more optional parameters later without changing this method's shape.
- Indexes: **no new Flyway migration** — `idx_credentials_title`, `idx_credentials_category`, and the composite `idx_credentials_user_id_deleted` already existed from `V1__init.sql` (S0.3), so the P1.5 prompt's "if not already in V1" condition was false. Confirmed live via `\d credentials`.
- `docs/db-design.md` → Index rationale gets an added honest caveat: `idx_credentials_title` is a plain btree, which only accelerates *prefix* matches — S1.5's search is a *contains* match (`LIKE '%term%'`), so this index doesn't actually serve it. A `pg_trgm` trigram index would, but adding a new Postgres extension is out of this session's scope — flagged for later if search performance ever matters at real data volume.
**Files:** `vault/dto/CredentialCreateRequest.java`, `vault/CredentialRepository.java`, `vault/CredentialService.java`, `vault/CredentialServiceImpl.java`, `vault/CredentialController.java`, `docs/decisions.md` (ADR-011), `docs/db-design.md`, `postman/SecureVault.postman_collection.json`, `docs/api-contract.md`, `docs/evidence/milestone-1/s1.5-*`.
**Decisions:** ADR-011 (`EnumType.STRING` over `ORDINAL`).
**Verified (evidence in `docs/evidence/milestone-1/`):**
- Create with explicit `category: BANKING` / `WORK` → persisted correctly (`s1.5-0-*`, `s1.5-1-*`).
- Search `"git"` and `"GIT"` → identical results (both `GitHub (work)` and `GitLab`), proving case-insensitivity (`s1.5-2-*`, `s1.5-3-*`).
- Search with no matches → **200**, empty list, not an error (`s1.5-4-*`).
- Filter `category=BANKING` → only Chase Bank; `category=OTHER` → only the two OTHER-category rows (`s1.5-5-*`, `s1.5-6-*`).
- Second user (Erin, zero credentials) searching/filtering → empty results, never Dave's rows (`s1.5-7-*`, `s1.5-8-*`).
- `\d credentials` → all three required indexes present (`s1.5-9-*`).
**Blockers:** none.
**Commit:** _batched — see Phase 1 close, ADR-008._

### S1.6 — 2026-08-11 — Milestone 1 evidence pack
**Mentor task:** M-14 — the Milestone 1 completion checklist. No new features; this session proves what exists and fixes only real gaps found while proving it.
**Done:**
- Walked the mentor's checklist (Project / Authentication / Vault / Security / Quality) against the live app — full results in `docs/evidence/milestone-1/CHECKLIST.md`.
- Found 3 honest GAPs, all in Quality, all fixed with the smallest correct change:
  1. **No Bean Validation anywhere** — added `@Valid` + `@NotBlank`/`@Email`/`@Size` to `UserRegisterRequest`, `LoginRequest`, `CredentialCreateRequest` (full coverage stays S2.2/M-25).
  2. **No catch-all exception handling** — added `common/exception/GlobalExceptionHandler` (basic `@RestControllerAdvice`: validation failures + a logged catch-all), leaving the existing per-controller business-exception handlers alone (consolidation is still S2.3/M-26).
  3. **Postman collection had no environment file or saved example responses** — added `postman/SecureVault.postman_environment.json` and populated all 19 requests' example responses from this phase's real captured evidence (no fabricated examples).
- Everything else on the checklist was already a true PASS — no other gaps found. Confirmed soft delete, MFA, sharing, etc. are correctly *not* expected yet (later phases per `docs/roadmap.md`), so their absence isn't a gap.
- Demonstrated the full journey in one continuous run: register → login → create → DB shows ciphertext → decrypt → update (password) → delete → confirmed gone (404).
**Files:** `user/dto/UserRegisterRequest.java`, `security/dto/LoginRequest.java`, `vault/dto/CredentialCreateRequest.java`, `user/UserController.java`, `security/AuthController.java`, `vault/CredentialController.java`, `common/exception/GlobalExceptionHandler.java` (new), `postman/SecureVault.postman_collection.json`, `postman/SecureVault.postman_environment.json` (new), `docs/evidence/milestone-1/CHECKLIST.md` (new) + `docs/evidence/milestone-1/s1.6-*`.
**Decisions:** none new — the gap fixes are explicitly scoped as "minimum to make the checklist truthful," not early completion of S2.2/S2.3's real work.
**Verified:** `mvn clean verify` green. Blank/malformed register request → 400 with per-field `VALIDATION_FAILED` messages instead of an unhandled exception or bad data reaching the DB (`s1.6-gap-fix-validation-400.txt`). Full continuous journey evidence in `s1.6-journey-*`.
**Blockers:** none. **Milestone 1 is complete** — all checklist lines PASS.
**Commit:** _batched — see Phase 1 close, ADR-008._

### S2.1 — 2026-08-11 — DTO layer + MapStruct mappers
**Mentor tasks:** M-24, M-28
**Done:**
- Added MapStruct (`mapstruct` + `mapstruct-processor` 1.6.3, `lombok-mapstruct-binding` 0.2.0) to `backend/pom.xml`, with an explicit `maven-compiler-plugin` `annotationProcessorPaths` ordering Lombok → mapstruct-processor → the binding jar, so Lombok's generated accessors are visible to MapStruct in the same compile pass (master §20's known-issue table warns this silently produces empty mappers without the binding jar).
- `UserMapper` (`user/`) and `CredentialMapper` (`vault/`) — `@Mapper(componentModel = "spring")`, generated at compile time. Every field needing encryption/decryption (`encryptedPassword` ↔ plaintext) is `@Mapping(target = ..., ignore = true)` in the mapper and set explicitly in the service — mappers stay free of crypto and business logic, per the prompt's explicit instruction.
- `CredentialUpdateRequest → Credential` uses `@BeanMapping(nullValuePropertyMappingStrategy = IGNORE)` — replaces S1.4's five hand-written `if (x != null)` checks with the equivalent generated code (verified identical behavior against the generated `CredentialMapperImpl`).
- New `CredentialSummaryResponse` (list/search view) split out from `CredentialResponse` (create/update view) — same fields today, but now two independently-evolvable API contracts, per the prompt's explicit ask.
- Removed the static `from(...)` factory methods from `UserResponse`, `CredentialResponse`, `CredentialDetailResponse` — all mapping now goes through the injected mapper.
- Every controller method already took/returned only DTOs going into this session (S1.1-S1.6 never leaked an entity); confirmed still true after the refactor.
**Files:** `backend/pom.xml`, `user/UserMapper.java` (new), `user/UserServiceImpl.java`, `user/dto/UserResponse.java`, `vault/CredentialMapper.java` (new), `vault/CredentialService.java`, `vault/CredentialServiceImpl.java`, `vault/CredentialController.java`, `vault/dto/CredentialResponse.java`, `vault/dto/CredentialDetailResponse.java`, `vault/dto/CredentialSummaryResponse.java` (new), `docs/decisions.md` (ADR-012).
**Decisions:** ADR-012 (MapStruct for DTO↔Entity mapping, records as DTOs, split list/detail responses — full reasoning for *why* mapping exists, per the mentor's explicit ask).
**Verified:** `mvn clean compile` generates `UserMapperImpl`/`CredentialMapperImpl`; inspected the generated source directly — `toEntity` correctly uses `Credential.builder()` (MapStruct auto-detects the Lombok `@Builder`), so `category`/`favorite`/`deleted` retain their `@Builder.Default` values exactly as S1.3-S1.5 intended. `mvn test` green (`AesEncryptionServiceTest`).
**Blockers:** none.
**Commit:** _batched — see Phase 2 close, ADR-008._

### S2.2 — 2026-08-11 — Bean Validation (full coverage)
**Mentor task:** M-25
**Done:**
- `UserRegisterRequest.password`: `@Size(min = 8, max = 72)` (72 = BCrypt's hard byte limit — accepting a longer password would silently lie about what's actually checked) + `@Pattern` four-class complexity (upper/lower/digit/special).
- `LoginRequest.password`: deliberately **stays presence-only** (`@NotBlank`, no `@Pattern`/`@Size`) — a user whose password predates a later policy tightening must still be able to log in; complexity is checked once, at registration.
- `CredentialCreateRequest`/`CredentialUpdateRequest`: `@Size` bounds matching the DB column lengths (title 150, username 150, websiteUrl 255, notes capped at 2000 as an app-level guard on the unbounded `TEXT` column), `@URL` on `websiteUrl` (optional — only validated when present). `password` deliberately has **no `@Pattern`** on either DTO: it's a secret for a third-party site, not the SecureVault account password — the app has no authority to demand it meet a complexity policy. `CredentialUpdateRequest` uses `@Size(min = 1, ...)` instead of `@NotBlank` throughout, since `null` (field omitted) must still pass — only a present-but-blank value should fail.
- Wrote `docs/validation.md` — annotation-by-annotation table per DTO, plus the two questions the mentor explicitly asks interns to be able to answer: `@NotBlank` vs `@NotNull`, and `@Size` vs `@Pattern`.
**Files:** `user/dto/UserRegisterRequest.java`, `security/dto/LoginRequest.java`, `vault/dto/CredentialCreateRequest.java`, `vault/dto/CredentialUpdateRequest.java`, `docs/validation.md` (new).
**Decisions:** none new — validation bounds documented inline and in `docs/validation.md`, not ADR-level (no locked decision changed).
**Verified:** curl evidence for a blank/malformed register request and a blank-title/bad-URL credential create request, both 400 with exact per-field messages, in `docs/evidence/milestone-2/`.
**Blockers:** none.
**Commit:** _batched — see Phase 2 close, ADR-008._

### S2.3 — 2026-08-11 — Exceptions + global handler + response envelope
**Mentor tasks:** M-26, M-27
**Done:**
- `common/exception/ErrorCode` — the exact fixed enum from master §9. `common/exception/BusinessException` (abstract) carries its own `ErrorCode` + `HttpStatus`. Five concrete subclasses moved out of their feature packages into `common.exception`: `UserNotFoundException` (new — not yet thrown anywhere; ready for a later phase's direct user-lookup endpoint), `CredentialNotFoundException`, `DuplicateEmailException`, `InvalidCredentialsException`, and a **generic** `AccessDeniedException` (replaces S1.4's `CredentialAccessDeniedException` — master §9 defines exactly one `ACCESS_DENIED` code shared by every entity, unlike "not found," which stays per-entity).
- `GlobalExceptionHandler` rewritten: one `@ExceptionHandler(BusinessException.class)` covers every business exception (each already carries its own status/code), plus `MethodArgumentNotValidException`, `ConstraintViolationException`, `HttpMessageNotReadableException` (malformed JSON / invalid enum value), `AuthenticationException` (safety net), the framework's own `org.springframework.security.access.AccessDeniedException` (referenced fully-qualified — collides by name with our own type), and a catch-all that logs a correlation UUID at `ERROR` and returns it in the client-facing message so a report can be matched to a log line without ever exposing a stack trace or internal class name.
- Removed every per-controller `@ExceptionHandler` stopgap from `UserController`, `AuthController`, `CredentialController` (all were explicitly marked `TODO(S2.3)` since the session that added them).
- `SecurityConfig` gets `AuthenticationEntryPoint`/`AccessDeniedHandler` beans that serialize the identical `ApiResponse` envelope by hand via the app's `ObjectMapper` — both run at the servlet-filter level, before `DispatcherServlet`, so `@RestControllerAdvice` never sees them; previously 401 was a bare `response.sendError(...)` with an empty body.
- `common/response/PagedResponse<T>` added (unused until S4.5, per the prompt's explicit ask to have it ready ahead of time).
- `DELETE /api/vault/{id}` keeps its bodyless `ResponseEntity<Void>` (204) as the sole exemption from "every controller returns `ApiResponse<T>`" — RFC 9110 §15.3.5 forbids a body on 204.
- Every `CredentialController` method now returns `ResponseEntity<ApiResponse<T>>` explicitly (previously `getById`/`list`/`search`/`update` returned bare `ApiResponse<T>`, which Spring still wraps as 200 but doesn't match the prompt's explicit ask).
**Files:** `common/exception/ErrorCode.java` (new), `common/exception/BusinessException.java` (new), `common/exception/UserNotFoundException.java` (new), `common/exception/CredentialNotFoundException.java` (moved), `common/exception/DuplicateEmailException.java` (moved), `common/exception/InvalidCredentialsException.java` (moved), `common/exception/AccessDeniedException.java` (new, replaces `vault/CredentialAccessDeniedException.java`, deleted), `common/exception/GlobalExceptionHandler.java`, `common/response/PagedResponse.java` (new), `config/SecurityConfig.java`, `user/UserController.java`, `user/UserServiceImpl.java`, `security/AuthController.java`, `vault/CredentialServiceImpl.java`, `vault/CredentialController.java`, `docs/decisions.md` (ADR-013).
**Decisions:** ADR-013 (consolidated `BusinessException` hierarchy, generic `AccessDeniedException`, envelope-consistent 401/403).
**Verified:** curl evidence for every status/error-code combination — 201/400/409 (register), 200/400/401 (login), 201/400/401 (create), 200/400/401/403/404 (get by id), 200/401 (list/search), 200/400/401/403/404 (update), 204/401/403/404 (delete), 500-with-correlation-id (unexpected). No-token and tampered-token 401 now return the full `ApiResponse` envelope instead of an empty body — confirmed via curl, captured in `docs/evidence/milestone-2/`.
**Blockers:** none.
**Commit:** _batched — see Phase 2 close, ADR-008._

### S2.4 — 2026-08-11 — Sweep and regression
**No new features.**
**Done:**
- Ran the W-4/P-AUDIT checklist (restricted to Phase 1-2 scope) by hand: no controller references an entity; every endpoint (except the justified 204 delete) is `ApiResponse`-wrapped; no exception handled outside `GlobalExceptionHandler`; every inbound DTO has Bean Validation; no `System.out`/`printStackTrace`; no password/token/secret ever logged; no `java.util.Random` in security-adjacent code; every `@ManyToOne` is `LAZY`; every multi-table write is `@Transactional`; package placement and naming match convention; no schema change without a migration (none needed this phase); no hardcoded secrets/URLs; no dead code (Spotless removes unused imports on every build); every endpoint documented.
- Found and fixed **two real HIGH findings** while manually exercising every endpoint (not from the checklist itself, but exactly the kind of drift W-4 exists to catch): `GET /api/vault/{id-as-non-numeric-string}` and `GET /api/vault/search` with no `?q=` both fell through to the catch-all `Exception` handler and returned **500**, when a client sending the wrong type or omitting a required parameter is a **400**. Added `MethodArgumentTypeMismatchException` and `MissingServletRequestParameterException` handlers to `GlobalExceptionHandler`. Re-verified both now return 400 with a field-level message.
- Re-ran the full Postman collection's requests via curl (no Postman GUI available this session either, same disclosed limitation as every prior session) and updated `postman/SecureVault.postman_collection.json`: fixed the two 401 examples whose saved bodies were empty (pre-S2.3 behavior) to the new `ApiResponse` envelope body, and added 5 new requests/examples covering behavior that only exists as of Phase 2 — register validation failure, vault-create validation failure, credential not-found (404), malformed path-variable id (400), and missing search `q` (400). Collection grew 19 → 24 requests.
- Regenerated `docs/api-contract.md` from the actual controllers — every row rewritten (added `400`/`VALIDATION_FAILED` to every endpoint that now validates, split list/search's response type to `CredentialSummaryResponse`), plus a new "Error responses, uniformly" section and the full `ErrorCode` reference.
- Updated `docs/guide.md`: the "Module map" and "API index" sections had been left as `**TBD**` placeholders since S0.1 despite six Phase-1 sessions shipping real code — filled in properly. Added a worked request-flow diagram (`JwtAuthenticationFilter` → `DispatcherServlet` → `Controller` → `Service` → `Mapper` → `Repository` → `Hibernate` → `PostgreSQL`, with the exception/envelope path called out) using `CredentialController#update` as the concrete example, per the prompt's explicit ask.
**Files:** `common/exception/GlobalExceptionHandler.java` (two new handlers), `docs/api-contract.md` (regenerated), `docs/guide.md` (module map, API index, request-flow diagram), `postman/SecureVault.postman_collection.json` (5 new requests, 2 fixed examples), `docs/evidence/milestone-2/*` (new).
**Decisions:** none new — both fixes are bug fixes surfaced by the audit, not decisions.
**Verified:** `mvn clean verify` green after every change in this phase, run again at phase close. Full curl-based journey re-run end to end (register → validation failure → login → 401s → vault CRUD → search/filter → cross-user 403 → 404 → type-mismatch 400 → missing-param 400 → 500-with-correlation-id) — evidence in `docs/evidence/milestone-2/`.
**Blockers:** none. **Phase 2 is complete.**
**Commit:** _batched — see Phase 2 close, ADR-008._

### S3.1 — 2026-08-11 — Password strength analyzer
**Mentor task:** M-29
**Done:**
- `PasswordStrengthService`/`Impl` in the new `password/` package: base score (+1 each for length>12, uppercase, lowercase, digit, special — the mentor's exact baseline formula), then penalties for consecutive repeats (3+ run), sequential patterns (4+ ASCII or keyboard-row run — 4, not 3, specifically so the mentor's own `Welcome123` worked example doesn't false-positive on its `123`), and a whole-password case-insensitive dictionary hit against a hand-curated ~250-entry `classpath:/password/common-passwords.txt` (not a downloaded wordlist, per the prompt).
- `entropyBits` = true Shannon entropy of the password's own character-frequency distribution, scaled by length — not the common charset-pool shortcut, since the prompt names "Shannon entropy" specifically.
- `POST /api/password/strength` — public (`permitAll`, like `/api/auth/**`): a strength check is a stateless utility that may run before a JWT exists (e.g. live registration-form feedback), and never touches user/vault data.
- `docs/password-policy.md` — exact algorithm, every threshold's reasoning, score→label table, worked examples.
- `PasswordStrengthServiceImplTest` — all 5 of the mentor's named cases plus a determinism check: `"password"` → 0/Very Weak with dictionary+no-variety feedback; `"Welcome123"` → exactly 3/Medium with length+special feedback; a 20-char random mixed password → 5/Very Strong; `"aaaaaaaa1A!"` → repetition-penalized; `"abcd1234"` → sequence-penalized.
**Files:** `password/PasswordStrengthService.java`, `password/PasswordStrengthServiceImpl.java`, `password/PasswordController.java`, `password/dto/PasswordStrengthRequest.java`, `password/dto/PasswordStrengthResponse.java`, `password/common-passwords.txt` (resource), `config/SecurityConfig.java` (permitAll), `docs/password-policy.md` (new), `docs/decisions.md` (ADR-014), `backend/src/test/.../PasswordStrengthServiceImplTest.java`.
**Decisions:** ADR-014 (penalty thresholds, whole-string dictionary match, true Shannon entropy — see above).
**Verified:** `mvn test` green, all 6 test cases pass on first run. Live curl confirms the exact same scores as the unit tests (`docs/evidence/milestone-2/s3-1-*` .. `s3-4-*`), including a 400 on a blank password.
**Blockers:** none.
**Commit:** _batched — see Phase 3 close, ADR-008._

### S3.2 — 2026-08-11 — Password generator
**Mentor task:** M-30
**Done:**
- `PasswordGeneratorService`/`Impl` — guarantee-then-fill-then-shuffle algorithm, entirely on one `SecureRandom` instance: (1) one character picked from each *enabled* class's pool first, guaranteeing every requested class appears (the exact bug a naive single-pool random fill cannot avoid, especially at the prompt's own 8-character minimum); (2) remaining slots filled from the union of enabled pools; (3) Fisher-Yates shuffle so the guaranteed characters from step 1 don't always land at the front.
- `excludeAmbiguous` strips `l`, `I`, `1`, `O`, `0` from whichever pool(s) contain them, before step 1 runs.
- `GenerateRequest` — `length` (`@Min(8) @Max(128)`), four boxed `Boolean` class flags (`@NotNull` — a security-relevant config should never silently default an omitted flag to false), and a new custom class-level `@AtLeastOneCharacterClass` Bean Validation constraint (a cross-field rule field-level annotations can't express) so a violation returns the same `400 VALIDATION_FAILED` shape as everything else instead of a one-off manual check.
- `POST /api/password/generate` — public, same reasoning as `/strength`. Reuses `PasswordStrengthService.analyze(...)` for the response's strength field — no duplicate scoring logic.
- **Gap found and fixed while exercising `GenerateRequest`'s validation live:** class-level constraint violations land in `BindingResult.getGlobalErrors()`, not `getFieldErrors()` — `GlobalExceptionHandler#handleValidation` was only reading field errors, so a "no character class enabled" violation returned `400` with an **empty** `errors[]` and no message at all. Fixed by concatenating field and global errors into one list.
- `java.util.Random` grep across the whole backend: **zero real usages** (one hit, in this class's own doc-comment explaining the rule — not actual code).
**Files:** `password/PasswordGeneratorService.java`, `password/PasswordGeneratorServiceImpl.java`, `password/PasswordController.java` (added `/generate`), `password/dto/GenerateRequest.java`, `password/dto/GenerateResponse.java`, `password/dto/AtLeastOneCharacterClass.java` + `AtLeastOneCharacterClassValidator.java`, `common/exception/GlobalExceptionHandler.java` (global-errors fix), `config/SecurityConfig.java` (permitAll), `docs/password-policy.md` (§2), `docs/decisions.md` (ADR-015), `backend/src/test/.../PasswordGeneratorServiceImplTest.java`.
**Decisions:** ADR-015 (guarantee-then-fill-then-shuffle algorithm and why a naive fill can't guarantee class coverage).
**Verified:** `mvn test` green — 1000 identical-config generations all distinct; every generated password satisfies its own config (length, classes present); disabling symbols never yields one; `excludeAmbiguous` removes all five characters over 100 generations; single-class-only generates from that class alone. Live curl confirms generation, the length-bound 400, and the (now-fixed) no-class-enabled 400 with a real message (`docs/evidence/milestone-2/s3-5-*` .. `s3-8-*`).
**Blockers:** none.
**Commit:** _batched — see Phase 3 close, ADR-008._

### S3.3 — 2026-08-11 — Strength integration into the vault
**Mentor task:** none numbered — P3.3 continuation of M-29
**Done:**
- `V2__add_password_changed_at.sql` — new column, backfilled to `now()` for any existing rows (disclosed limitation: their true original change date isn't recoverable). `strength_score` (already `SMALLINT` since `V1__init.sql`, previously unmapped) is now mapped on `Credential` as `Short` — **not** `Integer`: Hibernate's schema validation rejected an `Integer`-typed field against the existing `SMALLINT` column even with the types otherwise compatible, so the field is `Short` with an explicit `@JdbcTypeCode(SqlTypes.SMALLINT)` pin; `CredentialMapper` converts `Short`↔`Integer` automatically for the DTOs.
- `CredentialServiceImpl.create(...)` computes and stores `strengthScore` + sets `passwordChangedAt` unconditionally. `update(...)` only touches either field **inside** the existing decrypt-and-compare branch that already isolates "the password actually changed" (S1.4) — renaming a credential or changing its category must not reset either, and live testing confirmed exactly that (title-only update left `strengthScore` untouched).
- `strengthScore` added to `CredentialResponse` and `CredentialSummaryResponse` (not `CredentialDetailResponse` — not asked for, and the single-reveal endpoint already carries plenty).
- `GET /api/vault/health` — `VaultHealthResponse` (total, band counts, `reusedPasswordCount`, `staleCredentialCount`, `healthScore` 0-100). Reuse detection decrypts each credential's password, SHA-256 hashes it, groups by hash, and discards everything — plaintext/hashes exist only as loop-local variables, never logged/cached/returned. Staleness compares `passwordChangedAt` against a 90-day threshold. Health formula: 60% average strength + 25% non-reuse + 15% non-staleness, full reasoning in `docs/password-policy.md` §3 and ADR-016.
- `TODO(S4.6)` comments left at both strength-computation call sites, per the prompt's explicit instruction — bulk recomputation doesn't exist yet, but when it does it must go through the async executor, not the request thread.
**Files:** `db/migration/V2__add_password_changed_at.sql` (new), `vault/Credential.java`, `vault/CredentialMapper.java`, `vault/CredentialService.java`, `vault/CredentialServiceImpl.java`, `vault/CredentialController.java`, `vault/dto/CredentialResponse.java`, `vault/dto/CredentialSummaryResponse.java`, `vault/dto/VaultHealthResponse.java` (new), `docs/db-design.md`, `docs/password-policy.md` (§3), `docs/decisions.md` (ADR-016), `docs/api-contract.md`, `docs/guide.md` (module map, API index).
**Decisions:** ADR-016 (dedicated `password_changed_at` column vs. reusing `updated_at`; decrypt-hash-discard reuse detection; health score component weights).
**Verified, live, against real data (`docs/evidence/milestone-2/s3-9-*` .. `s3-18-*`):** created 3 credentials for one user — one dictionary-weak (`strengthScore: 0`), two sharing an identical strong password (`strengthScore: 5` each) — health showed `total=3, veryWeak=1, veryStrong=2, reused=2, healthScore=63` (hand-verified against the formula: 3.33-avg-score → 40.0 + (1-⅔)×25=8.33 + 15 = 63.33 → 63). Updated the weak credential's password to a strong one: `strengthScore` jumped 0→5, health recomputed to `veryStrong=3, healthScore=83` (60+8.33+15=83.33→83, matches exactly). A follow-up title-only update left `strengthScore` at 5, confirming it doesn't reset on unrelated edits. Backdated one credential's `password_changed_at` 100 days via direct SQL → `staleCredentialCount` became 1, `healthScore` recomputed to 78 (60+8.33+10=78.33→78, matches exactly). A zero-credential user's health returned `total=0, healthScore=100`, confirming per-user isolation and the empty-vault special case.
**Blockers:** none. **Phase 3 is complete.**
**Commit:** _batched — see Phase 3 close, ADR-008._

### S4.1 — 2026-08-11 — Transactions + AuditLog with rollback proof
**Mentor tasks:** M-31, M-32
**Done:**
- `common/audit/AuditLog` (new `V3__audit_logs.sql`): `action` (`AuditAction` enum, STRING), `entityType`, `entityId`, `performedBy`, `timestamp`, `ipAddress`, `userAgent`, `details`. No setters (immutable by construction) and **deliberately no FK** to `users`/`credentials` — an audit row must survive permanent deletion of the entity it describes (a requirement that only becomes concrete in S4.3, decided ahead of time here).
- `AuditService.record(...)` called synchronously from `CredentialServiceImpl`'s `create`/`update`/`delete`, inside the same `@Transactional` boundary — not an AOP aspect, because the mentor's actual requirement (audit failure rolls back the business write) only holds if both share one transaction. Full tradeoff reasoning: ADR-017.
- Rollback proof: a test-only `app.testing.force-audit-failure` flag (`@Value`, default `false`) in `AuditServiceImpl` that throws before persisting. Verified live: flag on → `POST /api/vault` returns 500, `credentials`/`audit_logs` row counts unchanged, no `RollbackProofTest` row exists; flag off → identical request succeeds, both tables gain exactly one row with matching ids.
**Files:** `common/audit/AuditAction.java`, `AuditLog.java`, `AuditLogRepository.java`, `AuditService.java`, `AuditServiceImpl.java` (all new), `db/migration/V3__audit_logs.sql` (new), `vault/CredentialServiceImpl.java`, `docs/decisions.md` (ADR-017), `docs/db-design.md`.
**Decisions:** ADR-017 (synchronous audit writes over AOP; no-FK `AuditLog` design; test-only rollback-proof flag).
**Verified:** `docs/evidence/milestone-2/s4-1-rollback-*` — before/attempt/after DB row counts, all three states captured against the real running app and Postgres.
**Blockers:** none.
**Commit:** _batched — see Phase 4 close, ADR-008._

### S4.2 — 2026-08-11 — Password history + reuse prevention
**Mentor tasks:** M-35, M-36
**Done:**
- `vault/PasswordHistory` (new `V4__password_history.sql`): unidirectional `@ManyToOne` to `Credential` only (no back-reference — see S4.4), immutable (no setters), unique `(credential_id, version)`. Deliberately **no `ON DELETE CASCADE`** — S4.3's permanent-delete must remove history explicitly, in order, before the credential; a plain FK makes the database itself enforce that ordering.
- Reuse check: `findTop5ByCredentialIdOrderByVersionDesc` (window capped at the query level, not in Java) runs before any mutation inside `update(...)`; a match throws `PasswordReusedException` (409, `PASSWORD_REUSED` — already in the fixed `ErrorCode` enum since Phase 2), rolling back any title/username/etc changes already applied in the same call.
- On a genuine password change: the credential's *current* ciphertext is copied into history as-is (no re-encryption — it's already correctly AES-GCM'd), version = last + 1 (or 1), then the new password is encrypted and set — all in the same transaction as the existing update() logic.
- `GET /api/vault/{id}/history` returns version + timestamp only, via a JPQL constructor-expression query that never selects `encrypted_password` into memory at all for that request — a stronger guarantee than a DTO that merely omits the field. Full reasoning: ADR-019.
**Files:** `vault/PasswordHistory.java`, `PasswordHistoryRepository.java`, `dto/PasswordHistoryVersionResponse.java` (all new), `db/migration/V4__password_history.sql` (new), `common/exception/PasswordReusedException.java` (new), `vault/CredentialService.java`, `CredentialServiceImpl.java`, `CredentialController.java`, `docs/decisions.md` (ADR-019), `docs/db-design.md`.
**Decisions:** ADR-019 (5-entry reuse window enforced at the query level; ciphertext reuse over re-encryption; history endpoint's stronger never-fetches-the-column hardening).
**Verified:** live sequence — 3 sequential password changes on one credential produced version rows 1, 2, 3; a title-only update created no history row; reusing the immediately-previous password → 409; reusing a 6th-oldest password (outside the 5-window) → allowed. `docs/evidence/milestone-2/s3-13-*` onward plus dedicated Phase 4 curl runs.
**Blockers:** none.
**Commit:** _batched — see Phase 4 close, ADR-008._

### S4.3 — 2026-08-11 — Soft delete, restore, trash, permanent delete
**Mentor tasks:** M-37, M-38, M-39
**Done:**
- `DELETE /api/vault/{id}` now sets `deleted=true`, `deletedAt=now()` instead of removing the row — still 204/no body (the established RFC 9110 §15.3.5 exemption).
- `PUT /api/vault/{id}/restore` — **no-op (200, unchanged state), not 409**, when called on an already-active credential: master §9's fixed `ErrorCode` enum has no code that fits "already active," and restore is naturally idempotent anyway. Full reasoning: ADR-018.
- `GET /api/vault/trash` — only the caller's soft-deleted credentials.
- `DELETE /api/vault/{id}/permanent` — hard-deletes the credential AND its password history in one transaction (history first, matching the FK ordering `ON DELETE CASCADE` was deliberately not used for in S4.2); only operates on an already-trashed credential (404 otherwise, same "no locked error code fits" reasoning as restore); audit logs are untouched by construction (no FK, ADR-017).
- Every "active" repository query renamed explicitly (`findByUserIdAndDeletedFalse`, `findByIdAndDeletedFalse`, `search(...)` with `AND c.deleted = false` in its JPQL) rather than a blanket `@Where`/`@SQLRestriction` on the entity — chosen specifically because trash/restore/permanent-delete need to see deleted rows, and a class-level filter would apply invisibly to them too. Full reasoning and the rejected alternative: ADR-018.
- Sharing interaction (point 7 of the prompt) is N/A — no sharing feature exists yet (Phase 5).
**Files:** `vault/CredentialRepository.java`, `CredentialService.java`, `CredentialServiceImpl.java`, `CredentialController.java`, `docs/decisions.md` (ADR-018), `docs/api-contract.md`.
**Decisions:** ADR-018 (explicit `*DeletedFalse` query naming over `@SQLRestriction`; no-op restore over a new error code).
**Verified:** full sequence live — create → soft delete → absent from list/search/filter/get-by-id (404) → present in trash → restore → present again, `strengthScore` unchanged → permanent delete → gone, history gone, audit logs still present and unchanged in count.
**Blockers:** none.
**Commit:** _batched — see Phase 4 close, ADR-008._

### S4.4 — 2026-08-11 — N+1 elimination and fetch strategy
**Mentor task:** M-33
**Done:**
- Reviewed every entity relationship: `Credential.user` (LAZY), `PasswordHistory.credential` (LAZY), `Credential` → `PasswordHistory` (**no mapping at all**, deliberately — the safest fetch strategy for a collection that would tempt exactly this session's N+1 bug is to not model it as a navigable relationship), `AuditLog` (no relationship, ADR-017). No `EAGER` anywhere.
- Added `historyCount` to `CredentialSummaryResponse` (number of password versions per credential) — a genuinely useful vault-list signal that also happens to be the "vault list touching related data" N+1 shape M-33 asks to find and fix.
- **Before:** temporarily added a `@OneToMany` back-reference + naive `credential.getPasswordHistories().size()` per row. First finding: with this project's `open-in-view: false` (locked since S0.1), the naive version doesn't just N+1 — it throws `LazyInitializationException` outright unless the read method is wrapped in `@Transactional`, which is itself a real cost. Wrapped temporarily to get a comparable count: **6 queries for 5 credentials** (1 list query + 5 per-row history queries).
- **After:** one batched `SELECT credential_id, COUNT(*) ... GROUP BY credential_id` aggregate query (`PasswordHistoryRepository.countByCredentialIds`), merged into the list results in `CredentialServiceImpl`. **2 queries, flat regardless of page size.** Temporary back-reference and `@Transactional` removed immediately after capturing the numbers — the shipped `Credential` entity still has no back-reference.
- Chose the aggregate-query technique over `JOIN FETCH`/`@EntityGraph` specifically because only a *count* was needed, not the history rows themselves; either of those would have pulled full entities into memory just to call `.size()`.
**Files:** `vault/dto/CredentialSummaryResponse.java`, `PasswordHistoryRepository.java`, `CredentialServiceImpl.java`, `docs/evidence/milestone-2/n-plus-one.md` (new, full before/after + relationship-mapping table).
**Decisions:** none new — full reasoning lives in `n-plus-one.md` per the prompt's own deliverable list, not a separate ADR.
**Verified:** `docs/evidence/milestone-2/n-plus-one.md`, `s4-4-nplusone-before-queries.txt`, `s4-4-nplusone-after-queries.txt`, `s4-4-lazyinit-exception.txt` — all captured against the real running app with `show-sql` (already local-only since S0.1).
**Blockers:** none.
**Commit:** _batched — see Phase 4 close, ADR-008._

### S4.5 — 2026-08-11 — Pagination, sorting, dynamic filtering
**Mentor task:** M-34
**Done:**
- `GET /api/vault` rewritten: `page`/`size`/`sortBy`/`direction`/`category`/`title`/`username`/`website`, all optional and freely combinable. `size` capped at 100 (`@Max`), `sortBy` whitelisted against real `Credential` fields via `@Pattern` (an unvalidated sort field is a 500 waiting to happen and a schema leak) — both via Bean Validation on `@RequestParam`s (`@Validated` added to `CredentialController`), reusing the existing `ConstraintViolationException` → 400 pipeline from Phase 2.
- `CredentialSpecifications` (static `Specification<Credential>` builders) composed via plain `if (filter != null) spec = spec.and(...)` chaining in `CredentialServiceImpl` — never string-built. Owner + `deleted=false` always ANDed first.
- `PagedResponse<T>` (unused since ADR-013/P2.3) finally consumed — fields renamed `page`/`size` → `currentPage`/`pageSize`, `+hasNext` added, to match the mentor's literal spec wording exactly (safe rename, confirmed unconsumed until this session).
- `DevDataSeeder` (`config/`, `@Profile("local")`, `CommandLineRunner`) seeds a fixed test user (`seed.user@securevault.local`) with 50 credentials across all 7 categories, idempotent (skips if already at target count), goes through `UserService`/`CredentialService` — the same code path a real request uses, not a direct repository save — so seeded rows get real hashing/encryption/strength scoring. **Found and fixed live:** the seeder crashed at startup with `IllegalStateException: No thread-bound request found`, because `AuditServiceImpl` constructor-injected `HttpServletRequest` (a request-scoped proxy) and the seeder runs outside any HTTP request. Fixed by looking up the request via `RequestContextHolder` per call, returning `null` ip/userAgent when none exists instead of crashing — see ADR-020.
**Files:** `vault/CredentialSpecifications.java` (new), `CredentialRepository.java` (+`JpaSpecificationExecutor`), `CredentialService.java`, `CredentialServiceImpl.java`, `CredentialController.java`, `common/response/PagedResponse.java`, `common/audit/AuditServiceImpl.java` (request-lookup fix), `config/DevDataSeeder.java` (new), `docs/decisions.md` (ADR-021), `docs/api-contract.md`.
**Decisions:** ADR-021 (Specification-based dynamic filtering; `PagedResponse` field finalization).
**Verified, live, against the 50-row seed:** pagination metadata (`currentPage`/`pageSize`/`totalElements`/`totalPages`/`first`/`last`/`hasNext`) correct at `page=1&size=10`; sort asc/desc by title both correct; category filter alone (7 BANKING rows); title filter alone (11 matches); combined category+title+sort+page query; invalid `sortBy` → 400 with the exact allowed-field list; `size=500` → 400 (capped at 100); page 99 → empty content, correct totals, not an error; a second, non-seed user's list never showed the seed user's 50 rows (cross-user isolation). `docs/evidence/milestone-2/s4-5-*`.
**Blockers:** none.
**Commit:** _batched — see Phase 4 close, ADR-008._

### S4.6 — 2026-08-11 — Async thread pool and background tasks
**Mentor tasks:** M-40, M-41
**Done:**
- `config/AsyncConfig`: `@EnableAsync` + `ThreadPoolTaskExecutor` bean `"taskExecutor"` — corePoolSize 4, maxPoolSize 8, queueCapacity 50, `CallerRunsPolicy`, thread names prefixed `sv-async-`, every value deliberately chosen and justified in the class javadoc (not defaults).
- `common/async/AsyncTaskService`: `sendNotificationEmail` (simulated — logs only, real SMTP is S5.6) wired into `UserServiceImpl.register(...)`; `logActivity` wired into `AuthController.login(...)` on success. Both `@Async("taskExecutor")`.
- `CredentialService.recomputeStrengthForUser` (resolves the `TODO(S4.6)` left in `create`/`update` since S3.3) — `@Async("taskExecutor")` + its own `@Transactional`, triggered by new `POST /api/vault/recompute-strength` (202 Accepted). **Deliberately NOT** applied to the single create/update path — moving strength computation off-thread there would mean the create/update response no longer reflects the just-computed `strengthScore`, a real regression; the bulk endpoint is the version that actually benefits from running off-thread.
- Critical boundary, documented and enforced: `AuditService.record(...)` stays synchronous — `@Async` runs in a different transaction, so an async audit write could never roll back with its business operation. Every async method takes `userId` as an explicit parameter (`SecurityContextHolder` is empty on the worker thread).
- Demonstrated pool reuse: 20 concurrent `POST /api/vault/recompute-strength` calls (plus the 2 from register/login) all landed on exactly 4 distinct `sv-async-N` thread names — proving reuse, not thread-per-task.
**Files:** `config/AsyncConfig.java` (new), `common/async/AsyncTaskService.java`, `AsyncTaskServiceImpl.java` (new), `common/util/LogMasking.java` (new), `user/UserServiceImpl.java`, `security/AuthController.java`, `vault/CredentialService.java`, `CredentialServiceImpl.java`, `CredentialController.java`, `docs/decisions.md` (ADR-020).
**Decisions:** ADR-020 (pool sizing; AuditService stays synchronous; explicit userId across the async boundary; the HttpServletRequest injection fix that fell out of this session's design).
**Verified:** `docs/evidence/milestone-2/s4-6-*` — masked welcome-email log line, login-activity log line (both on `sv-async-` threads, distinct from the `http-nio-8080-exec-` request thread), and the 20-concurrent-request thread-name histogram (4 distinct names, ~20 lines each).
**Blockers:** none.
**Commit:** _batched — see Phase 4 close, ADR-008._

### S4.7 — 2026-08-11 — Production logging
**Mentor tasks:** M-46, M-47
**Done:**
- `System.out`/`printStackTrace` grep: **0 before, 0 after** — genuinely already clean (consistent with every prior phase's finding), reported honestly rather than fabricating a nonzero "before."
- `@Slf4j` logging added across user registration, login success/failure (masked email), credential create/read/update/delete/restore/permanent-delete, and `GlobalExceptionHandler`'s `BusinessException` handler — DEBUG for a single credential read (high-frequency, not a state change), INFO for every state-changing event, WARN for business exceptions and failed logins, ERROR only for the unexpected catch-all.
- `common/util/LogMasking.maskEmail` (`u***@domain.com`) reused by both the async welcome-email log and the failed-login WARN.
- `config/CorrelationIdFilter` (`Ordered.HIGHEST_PRECEDENCE`, ahead of `JwtAuthenticationFilter`) puts one UUID per request into the SLF4J MDC — honors an incoming `X-Correlation-Id` — and echoes it in the response header; `GlobalExceptionHandler`'s catch-all now reads that same MDC value for its "Reference:" message instead of minting a fresh UUID (S2.3's original behavior).
- **Found and fixed live while capturing evidence:** an async task's log lines showed `[-]` instead of the request's correlation id — MDC is `ThreadLocal` and doesn't propagate to `@Async`'s worker threads automatically. Fixed with `MdcTaskDecorator`, wired into `AsyncConfig`'s executor; re-verified the same login's activity-log line on the `sv-async-` thread now carries the identical correlation id as the request-thread line.
- `logback-spring.xml`: console + `RollingFileAppender` (`SizeAndTimeBasedRollingPolicy`, 10MB-or-daily, 7-day history, 200MB total cap), per-profile levels (`DEBUG` `com.securevault` locally, `INFO` in prod) — `logging.level` removed from `application-local.yml` in favor of this single source of truth. `logs/` already gitignored since S0.1.
- Grepped the actual log file for every known test password/secret string used across this session's evidence-gathering — **0 matches**.
**Files:** `config/CorrelationIdFilter.java`, `MdcTaskDecorator.java`, `AsyncConfig.java` (decorator wiring) (all new/updated), `common/exception/GlobalExceptionHandler.java`, `common/util/LogMasking.java`, `user/UserServiceImpl.java`, `security/AuthController.java`, `vault/CredentialServiceImpl.java`, `backend/src/main/resources/logback-spring.xml` (new), `application-local.yml`, `docs/decisions.md` (ADR-022).
**Decisions:** ADR-022 (full never-logged list; correlation-id design; rotation policy).
**Verified:** `docs/evidence/milestone-2/s4-7-*` — correlation id echoed on both a caller-supplied and server-generated header, present in the log file for the request thread, present (post-fix) on the async thread too, and the clean secret-pattern grep result.
**Blockers:** none.
**Commit:** _batched — see Phase 4 close, ADR-008._

### S4.8 — 2026-08-11 — Milestone 2 evidence pack
**Mentor task:** none numbered — evidence/consolidation session, no new features.
**Done:**
- Ran a P-AUDIT-style sweep across the whole backend: no controller references an entity or returns an unwrapped endpoint; every `@ManyToOne` is `LAZY` (the one `@ManyToOne` grep hit was a doc-comment, not code); the four services without `@Transactional` (`AsyncTaskServiceImpl`, `AuditServiceImpl`, `PasswordGeneratorServiceImpl`, `PasswordStrengthServiceImpl`) reviewed and confirmed correctly exempt — `AuditServiceImpl` participates in its caller's ambient transaction by thread-bound context, not its own annotation; the other three touch no database at all. No new HIGH/MEDIUM findings beyond the ones already found and fixed live during S4.4-S4.7 (documented in their own session entries).
- Postman collection: added a `Password` folder (strength/generate — Phase 3 shipped without one), fixed `GET /api/vault - valid token (200)`'s saved example to the new `PagedResponse` shape (S4.5 changed it), and added 12 new Vault requests covering every Phase 4 endpoint (paginated/filtered list, invalid sortBy, size-cap, history, password-reuse 409, soft delete, trash, restore success + no-op, permanent delete success + 404, bulk recompute).
- `docs/db-design.md`: `audit_logs` and `password_history` moved from "documented now, migrated later" to "implemented," with the deviations from their original S0.3 plan called out explicitly (no FK on `audit_logs`, no `ON DELETE CASCADE` on `password_history` — both deliberate, both explained).
- `docs/guide.md`: module map, new "Async model" and "Logging" sections, database schema and API index counts updated.
- `docs/viva-notes.md` (new) — W-6 explain-back: BCrypt-vs-AES, request lifecycle, JWT filter/statelessness, why DTOs exist, and Phase 4's core design tension (synchronous audit vs. async activity logging).
- Central-repo push (P9.1) remains explicitly deferred — no mentor push/branch instruction yet (ADR-006), consistent with every prior phase.
**Files:** `postman/SecureVault.postman_collection.json`, `docs/db-design.md`, `docs/guide.md`, `docs/viva-notes.md` (new), `docs/api-contract.md`.
**Decisions:** none new.
**Verified:** `mvn clean verify` green at every step across the whole phase, run again at close. Full live journey re-confirmed end to end across all 8 sessions' evidence in `docs/evidence/milestone-2/`.
**Blockers:** none. **Phase 4 is complete. Milestone 2 (Phases 2-4) is complete.**
**Commit:** _batched — see Phase 4 close, ADR-008._

### S5.1 — 2026-08-11 — Credential sharing and permissions
**Mentor tasks:** M-42, M-43, M-44, M-45
**Done:**
- `credential_shares` table (V5) with a partial unique index `(credential_id, shared_with_user_id) WHERE active` — enforces no-duplicate-active-share without blocking re-sharing after a revoke.
- `AccessEvaluator`/`AccessLevel` (`sharing/`) — the single authorisation decision point: owner → allow; else active unexpired share + permission → allow/deny; else 403. Takes primitive ids, not entities, to avoid a `sharing↔vault` circular dependency.
- `CredentialServiceImpl.getByIdForUser`/`update` now route through `loadWithAccess`; delete/restore/permanent-delete/history stay strictly owner-only.
- `POST /api/share`, `GET /api/share/received`, `GET /api/share/sent`, `PUT /api/share/{id}`, `DELETE /api/share/{id}` (soft revoke).
- Shared reads are audited synchronously with the accessor's id (`AuditAction.ACCESS`, new enum value alongside `SHARE`/`REVOKE`).
- `permanentDelete` now also deletes the credential's shares first (no `ON DELETE CASCADE`, same reasoning as `password_history`).
**Files:** `sharing/*` (new package: entity, repo, service, controller, DTOs, events stub), `db/migration/V5__credential_shares.sql`, `common/exception/SelfShareNotAllowedException.java`, `common/exception/ShareAlreadyExistsException.java`, `common/exception/UserNotFoundException.java` (new email-based overload), `common/audit/AuditAction.java`, `vault/CredentialServiceImpl.java`
**Decisions:** ADR-023 (AccessEvaluator design, share-403 collapse, cascade cleanup)
**Verified:** Full master §12 matrix live — owner full access, READ view/denied-update, EDIT view/update/denied-delete, revoked/expired/unrelated all denied immediately; self-share 400; duplicate share 409; non-owner share attempt 403; soft-deleted credential unshareable and hidden from both lists; permanent delete cleans up shares (confirmed 0 rows remain) and its audit trail is untouched.
**Blockers:** none
**Commit:** _batched — see Phase 5 close, ADR-008._

### S5.2 — 2026-08-11 — Refresh tokens, logout, Redis denylist
**Mentor tasks:** (session, no numbered M-task — Phase 5 infrastructure)
**Done:**
- `refresh_tokens` table (V6) — SHA-256 hash only, never the raw token, plus a `token_family` UUID for reuse detection.
- Access tokens gained a `jti` (JWT ID) claim; `JwtAuthenticationFilter` checks it against a Redis denylist (`jwt:denylist:<jti>`, TTL = remaining lifetime) before honoring an otherwise-valid token.
- `POST /api/auth/refresh` (rotates, revoking the old token) and `POST /api/auth/logout` (revokes the refresh token + denylists the current access token).
- Reuse detection: replaying an already-rotated token revokes the entire family in one statement.
- **Fail-open** Redis denylist policy, explicit and documented (a Redis outage doesn't take the whole API down; a logged-out token can keep working for up to its remaining ≤15-min lifetime in that narrow window).
**Files:** `security/RefreshToken.java`, `RefreshTokenRepository.java`, `RefreshTokenService.java`/`Impl`, `TokenHasher.java`, `TokenDenylistService.java`/`Impl`, `security/dto/{RefreshRequest,LogoutRequest,TokenRefreshResponse}.java`, `JwtService.java` (jti), `JwtAuthenticationFilter.java`, `AuthController.java`, `db/migration/V6__refresh_tokens.sql`
**Decisions:** ADR-024 (rotation, reuse detection, fail-open denylist)
**Verified:** access token expires after 15 min → 401; refresh returns a working new token and rotates; the old refresh token then 401s; replaying it triggers family-wide revocation (confirmed the newer sibling token also dies); logout immediately denylists the current access token (confirmed via Redis `TTL`, ~15 min remaining) and revokes the refresh token; Redis stopped mid-session → login and authenticated calls still worked (fail-open confirmed live), documented WARN lines present.
**Blockers:** **Found and fixed live:** `@Transactional`'s default rollback-on-any-RuntimeException silently undid the family-wide revoke the instant `TokenInvalidException` was thrown — fixed with `noRollbackFor` (ADR-024).
**Commit:** _batched — see Phase 5 close, ADR-008._

### S5.3 — 2026-08-11 — Redis caching
**Mentor tasks:** (session, no numbered M-task — Phase 5 infrastructure)
**Done:**
- `RedisCacheConfig` — three regions (`vaultList` 5 min, `passwordStrength` 10 min, `dashboard` 2 min), JSON serialization, `disableCachingNullValues`.
- `CredentialServiceImpl.listForUser` cached, keyed on every filter param; `create`/`update`/`delete`/`restore` evict the whole `vaultList` region.
- `PasswordStrengthServiceImpl.analyze` cached, keyed by **SHA-256 hash of the password**, never the password itself.
- Consolidated three duplicate SHA-256-hex implementations into one shared `common/util/Sha256`.
- Cache hit/miss made visible via TRACE on `org.springframework.cache` (local profile).
**Files:** `config/RedisCacheConfig.java`, `common/util/Sha256.java`, `vault/CredentialServiceImpl.java`, `password/PasswordStrengthServiceImpl.java`, `security/TokenHasher.java` (refactored to reuse `Sha256`), `logback-spring.xml`
**Decisions:** none new this session (caching mechanics folded into ADR-025 once the deeper bugs below were found in S5.6/S5.7)
**Verified:** first list call MISS + populates cache (TRACE log), second call HIT with identical data; creating a credential evicts the cache, next call MISSes again and reflects the new item immediately; password-strength Redis value inspected directly — key is a hex hash, value contains only the analysis result, never the password.
**Blockers:** **Found and fixed live:** `GenericJackson2JsonRedisSerializer` needs `ObjectMapper.DefaultTyping.EVERYTHING`, not `NON_FINAL` — Java records are implicitly final, so `NON_FINAL` omitted `@class` for record DTOs nested in generic fields (`PagedResponse<T>.content`), causing an `InvalidTypeIdException` on the second (cache-hit) read even though the first (cache-miss) write silently "succeeded." Full reasoning in ADR-025 once S5.6/S5.7 surfaced the related transaction-boundary and authorization-ordering bugs.
**Commit:** _batched — see Phase 5 close, ADR-008._

### S5.4 — 2026-08-11 — MFA (TOTP) + device and session tracking
**Mentor tasks:** (session, no numbered M-task — Phase 5 infrastructure)
**Done:**
- TOTP via `dev.samstevens.totp` + zxing: `POST /api/auth/mfa/setup` (AES-encrypted secret, QR data URI, otpauth URI), `/verify` (enables MFA, issues 10 BCrypt-hashed backup codes shown once), `/disable` (requires a live code).
- Login flow branches on `user.mfaEnabled`: correct password → `mfaRequired=true` + a 2-min Redis-backed challenge token, no tokens yet; `POST /api/auth/mfa/challenge` exchanges challenge + code (TOTP or a backup code) for the real tokens.
- ±1 time-step clock-skew tolerance; Redis-backed replay guard (90s, covers the full skew window) rejects reusing an already-accepted code.
- Device/session tracking: `devices` table, upserted on every login (SHA-256 of User-Agent+IP as a fingerprint approximation), `GET /api/monitoring/devices`, `DELETE .../devices/{id}` revokes every refresh token that device ever minted.
**Files:** `security/{MfaBackupCode,MfaService,MfaServiceImpl,MfaChallengeService,MfaChallengeServiceImpl,BackupCodeGenerator}.java`, `security/dto/Mfa*.java`, `monitoring/{Device,DeviceRepository,DeviceService,DeviceServiceImpl,DeviceController}.java`, `user/User.java` (mapped `mfaEnabled`/`mfaSecret`), `security/{RefreshToken,RefreshTokenRepository,RefreshTokenService,RefreshTokenServiceImpl}.java` (device fingerprint threaded through), `AuthController.java`, `config/SecurityConfig.java` (mfa/setup-verify-disable now require auth, not blanket permitAll), `db/migration/V7__mfa_and_devices.sql`
**Decisions:** ADR-026 (TOTP library, AES-encrypted secret, peek/invalidate challenge tokens, Redis replay guard)
**Verified:** real QR/otpauth URI generated and a real TOTP code (computed independently via RFC 6238 in Python) accepted; wrong code rejected; enabling MFA issues 10 backup codes; full login-with-MFA round trip (password → challenge → code → real tokens); replaying the same code immediately rejected (replay guard); a backup code works once, then is rejected on reuse; device list and cross-user-protected revoke both verified, including that the revoked device's refresh token dies immediately.
**Blockers:** **Found and fixed live:** `consumeChallenge()` deleted the Redis challenge token on every lookup, including failed attempts — one wrong digit permanently burned the token instead of allowing a retry inside the 2-minute window. Fixed by splitting into `peekChallenge()`/`invalidateChallenge()` (ADR-026).
**Commit:** _batched — see Phase 5 close, ADR-008._

### S5.5 — 2026-08-11 — Security monitoring and anomaly detection
**Mentor tasks:** (session, no numbered M-task — Phase 5 infrastructure)
**Done:**
- `login_attempts` table, every attempt (success or failure) recorded; brute-force lockout after 5 failures in 15 min, auto-unlock after 30 min derived from `login_attempts`'s own timestamps (no new `locked_at` column).
- Locked accounts return the exact same generic `InvalidCredentialsException` as a wrong password — never disclosed.
- Four anomaly rules, each independently triggerable: new device/IP, elevated failed attempts (≥3, distinct from the ≥5 lockout), excessive vault access volume, mass permanent deletion — all via Redis `INCR`+`EXPIRE`+`SETNX`-guarded rate counters, each raising a persisted `SecurityAlert`.
- `GET /api/monitoring/login-attempts` (own; `?all=true` for ADMIN), `GET /api/monitoring/alerts`, `GET /api/monitoring/risk-score` (documented additive formula, no ML).
**Files:** `monitoring/{LoginAttempt,LoginAttemptRepository,LoginAttemptService,LoginAttemptServiceImpl,SecurityAlert,AlertType,AlertSeverity,SecurityAlertRepository,SecurityAlertService,SecurityAlertServiceImpl,SecurityAlertRaisedEvent,VaultAnomalyDetector,VaultAnomalyDetectorImpl,MonitoringController}.java`, `user/User.java` (mapped `accountLocked`/`failedLoginAttempts`), `security/{UserPrincipal,CustomUserDetailsService,AuthController}.java`, `vault/CredentialServiceImpl.java` (anomaly detector hooks), `db/migration/V8__login_attempts_and_alerts.sql`
**Decisions:** ADR-027 (no `locked_at` column, Redis anomaly-rate-counter pattern)
**Verified:** 5 wrong passwords → account locked, alerts raised at 3/4/5/6 failures (3-4 `ELEVATED`, 5+ `BRUTE_FORCE_LOCKOUT`); 6th attempt with the *correct* password still 401, identical response to a wrong password; 51 rapid reads on one credential → exactly one `EXCESSIVE_VAULT_ACCESS` alert (not 51); 5 permanent deletes in a short window → one `MASS_PERMANENT_DELETE` alert; second login from a different User-Agent → exactly one `NEW_DEVICE` alert (first-ever device correctly silent); risk-score formula verified against hand-computed expected values.
**Blockers:** none
**Commit:** _batched — see Phase 5 close, ADR-008._

### S5.6 — 2026-08-11 — Notifications and async email
**Mentor tasks:** (session, no numbered M-task — Phase 5 infrastructure)
**Done:**
- `notifications` table + `GET /api/notifications`, `PUT .../{id}/read`, `PUT .../read-all`.
- `NotificationEventListener` — one component, four `@TransactionalEventListener(phase = AFTER_COMMIT)` handlers covering all five required triggers (new-device and security-alert both arrive as `SecurityAlertRaisedEvent`; credential-shared/share-revoked/password-expiry each get their own event).
- Email via `JavaMailSender` against MailHog locally, dispatched `@Async("taskExecutor")`; failures logged at WARN, never break the business operation.
- `PasswordExpiryScheduler` (`@Scheduled`, daily by default) sweeps for credentials >90 days old, Redis-guarded to at most one notification per user per 7 days.
**Files:** `notification/*` (new package: entity, repo, service, controller, `EmailService`/`Impl`, `NotificationEventListener`, `PasswordExpiryCheckService`/`Impl`, `PasswordExpiryScheduler`, DTOs, events), `sharing/{CredentialSharedEvent,ShareRevokedEvent,CredentialShareServiceImpl}.java`, `vault/CredentialRepository.java` (stale-credential aggregate query), `SecureVaultApplication.java` (`@EnableScheduling`), `application.yml` (mail config), `db/migration/V9__notifications.sql`
**Decisions:** ADR-025 (shared with S5.3/S5.7 — the AFTER_COMMIT/REQUIRES_NEW half), ADR-028 (event model, password-expiry sweep)
**Verified:** all five triggers produce both a persisted `Notification` row and a real email visible in MailHog — credential-shared, share-revoked, new-device, security-alert (brute-force), password-expiry (via a temporary fast-cron override for live verification, then reverted); mark-read/mark-all-read confirmed; a wrong-code MFA disable attempt correctly does *not* fire a notification.
**Blockers:** **Found and fixed live:** `NotificationServiceImpl.create()` (plain `@Transactional`) returned successfully with a `null` id and no row ever reached Postgres — `@TransactionalEventListener(AFTER_COMMIT)` runs while the just-committed transaction's resources can still be thread-bound, so the default `REQUIRED` propagation silently "participated" in a transaction that was already finished. Fixed with `Propagation.REQUIRES_NEW` (ADR-025).
**Commit:** _batched — see Phase 5 close, ADR-008._

### S5.7 — 2026-08-11 — Analytics dashboard APIs
**Mentor tasks:** (session, no numbered M-task — Phase 5 infrastructure)
**Done:**
- `GET /api/dashboard/summary` (total, by-category, favorites, shared in/out, trash count, last login), `/password-health` (band counts, reused/stale, health score, top-5-to-fix), `/recent-activity` (last 20 audit entries, human-readable), `/alerts` (delegates to the S5.5 service).
- `GET /api/admin/stats` (ADMIN only) — users, active sessions, failed logins 24h, alerts by severity, system health.
- Every count is a database `GROUP BY`/aggregate query, never a full-table load followed by in-memory grouping (the one bounded exception: top-5-to-fix sorts a single user's own already-loaded active-credential list, same as `getHealth()`).
- `summary`/`password-health`/admin-stats cached 2 min in the `dashboard` region.
**Files:** `dashboard/*` (new package), `admin/{AdminController,AdminStatsService,AdminStatsServiceImpl,AdminStatsResponse}.java`, `vault/CredentialRepository.java` (grouped-count queries), `security/RefreshTokenRepository.java`/`monitoring/{LoginAttemptRepository,SecurityAlertRepository}.java` (admin-stats queries), `common/audit/AuditLogRepository.java` (top-20 query)
**Decisions:** ADR-029 (aggregate-in-database rule, 2-min cache, no active eviction)
**Verified:** all four dashboard endpoints against a real multi-category, multi-strength vault — correct category breakdown, correctly-ranked top-5-to-fix, human-readable activity descriptions, empty alerts for a clean account; non-admin → 403 on `/api/admin/stats`, admin (promoted via DB) → 200 with correct aggregate numbers.
**Blockers:** **Found and fixed live:** caching `AdminController.stats()` as a single method meant a warm cache entry (from an earlier admin call) served a genuinely different **non-admin** user 200 with real stats — the `@Cacheable` proxy never re-ran the method body, so the role check inside it never re-ran either. Fixed by splitting the ADMIN check (uncached, controller) from the computation (cached, a separate `AdminStatsServiceImpl` bean) — re-verified live that a fresh non-admin still gets 403 even with a warm cache (ADR-025).
**Commit:** _batched — see Phase 5 close, ADR-008._

### S5.8 — 2026-08-11 — OpenAPI documentation and admin module
**Mentor tasks:** (session, no numbered M-task — Phase 5 infrastructure)
**Done:**
- `springdoc-openapi-starter-webmvc-ui` — `bearerAuth` HTTP/bearer security scheme, `@Tag` on every controller grouping Swagger UI by feature module (8 tags), title/version/description.
- `GET /api/admin/users` (paginated, `Specification`-based email/name search), `PUT /api/admin/users/{id}/status` (lock/activate, resets the failure counter on unlock), `GET /api/admin/audit-logs` (filterable by user/action/date range via `Specification`).
- `@EnableMethodSecurity` + `@PreAuthorize("hasRole('ADMIN')")` on all four `AdminController` routes, replacing the manual role check S5.7 used before method security existed.
- Swagger UI/API docs disabled under the `prod` profile.
**Files:** `config/OpenApiConfig.java`, `admin/{AdminController,AdminUserService,AdminUserServiceImpl,AdminAuditLogService,AdminAuditLogServiceImpl}.java`, `admin/dto/{AdminUserResponse,AdminUserStatusUpdateRequest,AdminAuditLogResponse}.java`, `user/{UserRepository,UserSpecifications}.java`, `common/audit/{AuditLogRepository,AuditLogSpecifications}.java`, `config/SecurityConfig.java` (`@EnableMethodSecurity`), every `@RestController` (`@Tag` added), `application-prod.yml`
**Decisions:** ADR-030 (method security, manual-check retirement, Specification-based filtering)
**Verified:** `GET /v3/api-docs` is valid OpenAPI 3.1 JSON, 39 paths, 8 tags, `bearerAuth` scheme present; Swagger UI loads (200); a genuinely separate non-admin user gets 403 on all four `/api/admin/**` routes, an admin gets 200 with correct paginated/filtered data on all four, including lock/unlock actually taking effect on subsequent login attempts.
**Blockers:** none (the one real bug this session touched — cache bypassing the admin check — was S5.7's `/stats` endpoint, fixed there and confirmed still holding once `@PreAuthorize` replaced the manual check)
**Commit:** _batched — see Phase 5 close, ADR-008._
