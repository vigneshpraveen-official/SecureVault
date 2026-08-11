# SecureVault — Progress Log

## CURRENT STATE
- Phase: 2 — Production-grade API refactor (**complete**)
- Last session: S2.4 (sweep + Postman regression)
- Build: green | Tests: 3 passing (`AesEncryptionServiceTest`) | Migrations applied: V0, V1 (users, credentials) — no schema change this phase
- Working branch: main (personal fork repo only; no central-repo remote configured yet — see ADR-006)
- Next session: S3.1 — Password strength analyzer (M-29)
- Open blockers: ERD PNG export is a manual dbdiagram.io step, not yet done by the developer (DBML source is committed)
- Commit cadence: **one commit per phase**, not per session (ADR-008, developer's explicit preference from Phase 1 on) — Phase 2's four sessions land in a single commit
- Full phase/milestone tracker: `docs/roadmap.md` (13/53 sessions done, Milestone 1 complete, **Phase 2 complete**)

## NEXT UP
1. S3.1 — Password strength analyzer (M-29)
2. S3.2 — Generator with `SecureRandom` (M-30)
3. S3.3 — Entropy + vault integration

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
