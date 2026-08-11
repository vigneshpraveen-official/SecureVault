# SecureVault — Operator Guide

The single source of truth for how to actually run this project. Rewritten in the same
session that changes any command, env var, or architecture element it describes — a stale
guide is worse than none (`docs/securevault_master.md` §14).

## Prerequisites

- Java 21 (verified this session: OpenJDK 21.0.11)
- Maven 3.9+ (verified this session: Apache Maven 3.9.12)
- Docker + Docker Compose v2 (verified this session: Docker 29.7.2 / Compose v5.3.1)
- Git

## First-time setup

```bash
git clone https://github.com/vigneshpraveen-official/SecureVault.git
cd SecureVault
cp .env.example .env
```

Fill in `.env`:
- `DB_PASSWORD` — any local value; it seeds the Docker Postgres container's password.
- `JWT_SECRET` / `AES_SECRET_KEY` — generate locally, never share or commit:
  ```bash
  openssl rand -base64 32   # run twice, once per variable
  ```
- Leave `MAIL_*` pointed at MailHog for local dev (see below); `APP_CORS_ORIGINS` and
  `SERVER_PORT` can keep their defaults.

Optionally copy `backend/src/main/resources/application-local.yml.example` to
`application-local.yml` in the same directory if you want to override the local datasource
without touching `.env` (that file is gitignored).

## Daily workflow

Work happens in atomic sessions, done daily or whenever you have time — there's no fixed
schedule, but each session should start and end clean:

1. Open `docs/progress.md` → **CURRENT STATE** to see the last completed session and what's next.
2. (Optional, for the big picture) Check `docs/roadmap.md` to see where that session sits
   among all 53 sessions / 10 phases.
3. Copy that session's prompt from `docs/securevault_prompts.md` and paste it as-is into
   whichever AI tool you're using today (Claude Code, Gemini, ChatGPT, Antigravity — see
   `docs/ai/CONTEXT.md`, which every tool reads first via its pointer file).
4. Let the agent state its plan and wait for your approval before it writes anything
   touching security, schema, or a locked decision.
5. At the end, the agent runs Session Close (W-2): green build, `docs/progress.md` +
   `docs/roadmap.md` + other affected docs updated, one commit proposed for your approval.

This is what keeps multiple AI tools consistent across sessions — the state lives in these
files, not in any one tool's memory.

## Daily run commands

Start the Docker services (PostgreSQL, Redis, MailHog):
```bash
docker compose --env-file .env up -d
docker compose ps          # all three should show "healthy"
```

Run the backend (loads `.env` into the shell, then runs Spring Boot on the `local` profile):
```bash
cd backend
set -a && source ../.env && set +a
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Verify it's up:
```bash
curl http://localhost:8080/actuator/health   # {"status":"UP"}
```

Format Java code before committing (Spotless, bound to `verify` so drift fails the build):
```bash
mvn -f backend/pom.xml spotless:apply     # apply formatting
mvn -f backend/pom.xml clean verify       # compile + spotless:check
```

Stop the Docker services:
```bash
docker compose down          # add -v to also drop the data volumes
```

MailHog web UI (catches all local dev email): http://localhost:8025

Run the frontend (Phase 6, `frontend/`):
```bash
cd frontend
npm install                  # first time only
cp .env.example .env.local   # VITE_API_BASE_URL — defaults to http://localhost:8080
npm run dev                  # Vite dev server on http://localhost:5173
```
Production build (also the check for source-map leakage and console-log residue, S6.8):
```bash
npm run build                # dist/ — no source maps (vite.config.js build.sourcemap=false)
npm run lint                 # oxlint
```
Backend CORS already defaults `APP_CORS_ORIGINS` to `http://localhost:5173`, so no extra
configuration is needed to run both together locally.

## Environment variables

See `.env.example` for the authoritative, commented list. Summary:

| Variable | Purpose |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL connection |
| `JWT_SECRET`, `JWT_ACCESS_EXPIRY_MS`, `JWT_REFRESH_EXPIRY_MS` | JWT signing key + token lifetimes |
| `AES_SECRET_KEY` | AES-256-GCM master key for vault secret encryption |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | Redis connection (denylist/cache, from Phase 5) |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | SMTP (MailHog locally; real SMTP from Phase 5) |
| `APP_CORS_ORIGINS` | Allowed frontend origin(s) |
| `SERVER_PORT` | Backend HTTP port |

`JWT_SECRET` and `AES_SECRET_KEY` have **no default** in `application.yml` — the app fails
fast at startup if either is missing, by design.

Frontend (`frontend/.env.example`):

| Variable | Purpose |
|---|---|
| `VITE_API_BASE_URL` | Base URL the axios client (`src/api/client.js`) targets — never hardcoded in source |

## Architecture overview

```
Browser
  │
  ▼
React 18 + Vite (frontend/ — live, Phase 6) — Redux Toolkit (auth/vault slices, thunks),
  React Router (ProtectedRoute/AdminRoute), one axios instance with a request-token
  interceptor + a response interceptor that queues concurrent 401s behind a single
  /api/auth/refresh call, retries them, and redirects to /login on refresh failure
  │  HTTPS /api/**
  ▼
Spring Boot 3.5 (backend/) — monolith, feature-first packages
  ├── config/      Security (+ method security), Async, RedisCache, OpenAPI, CORS
  ├── common/      response envelope, exceptions, audit, util
  ├── security/    JWT service/filter, refresh tokens, MFA, AES-GCM crypto
  ├── user/ vault/ password/ sharing/ notification/ monitoring/ dashboard/ admin/ report/
  ▼
PostgreSQL 16 (Docker locally / Neon in prod) — Flyway-migrated, ddl-auto=validate
Redis 7 (Docker locally / Upstash in prod) — JWT denylist, vault-list/strength/dashboard cache,
                                              MFA challenge/replay guard, anomaly rate counters
MailHog (Docker locally / real SMTP in prod) — async email (Phase 5)
```

Full reasoned layer diagram is in `docs/architecture.md` (S0.2). Below is the request flow
with the real classes involved, as of Phase 2 (P2.4/S2.4) — every layer, in order, for a
typical authenticated request:

```
Client (curl / Postman / future React)
  │  HTTP request, e.g. PUT /api/vault/7  { "title": "New Title" }
  ▼
JwtAuthenticationFilter (security/)          — OncePerRequestFilter, before DispatcherServlet
  │  no/invalid token → SecurityContext left empty → AuthenticationEntryPoint bean writes a
  │  401 ApiResponse envelope directly to the response and the chain stops here (config/SecurityConfig)
  │  valid token → SecurityContext populated with UserPrincipal, chain continues
  ▼
DispatcherServlet → HandlerMapping → CredentialController#update(...)   (vault/)
  │  @Valid @RequestBody CredentialUpdateRequest — Bean Validation runs before the method body;
  │  a failure short-circuits straight to GlobalExceptionHandler#handleValidation (never reaches
  │  the controller body) — see docs/validation.md
  │  @AuthenticationPrincipal UserPrincipal — userId comes from the JWT, never the request body
  ▼
CredentialServiceImpl#update(...)             (vault/) — @Transactional
  │  loadOwned(id, userId) — 404 (CredentialNotFoundException) if missing,
  │  403 (AccessDeniedException) if it exists but isn't the caller's
  │  CredentialMapper#updateEntityFromRequest — MapStruct copies non-null request fields onto
  │  the managed entity (null = "leave unchanged", S1.4); password re-encryption (decrypt +
  │  compare + AesEncryptionService#encrypt) stays in the service, never in the mapper
  ▼
CredentialRepository (vault/) — Spring Data JPA
  ▼
Hibernate → PostgreSQL 16 — schema owned by Flyway, ddl-auto=validate only
  ▲
  │  updated Credential entity returns back up the stack
CredentialMapper#toResponse(...) → CredentialResponse                    (vault/dto/)
  ▲
CredentialController wraps it: ResponseEntity.ok(ApiResponse.success(...))
  ▲
Client receives 200 { "success": true, "data": { ... }, ... }
```

Anything that goes wrong anywhere in the Controller → Service → Repository chain (a business
exception, a Bean Validation failure, a malformed body, an unexpected bug) is caught by
`common/exception/GlobalExceptionHandler` (`@RestControllerAdvice`) and rendered in the same
`ApiResponse` envelope — see `docs/api-contract.md` → "Error responses, uniformly". Only the
two exceptions that fire *before* `DispatcherServlet` (401 unauthenticated, 403 from Spring
Security's own access-control layer) bypass the `@RestControllerAdvice` entirely; those are
handled by the `AuthenticationEntryPoint`/`AccessDeniedHandler` beans in `SecurityConfig`
instead, which write the identical envelope shape by hand.

## Module map

| Package | Contains | Status |
|---|---|---|
| `config/` | `SecurityConfig` (JWT filter chain, CORS, `AuthenticationEntryPoint`/`AccessDeniedHandler`), `AsyncConfig` (`taskExecutor` bean, S4.6), `MdcTaskDecorator` (correlation id across the async boundary, S4.7), `CorrelationIdFilter` (S4.7), `DevDataSeeder` (`local`-profile-only, S4.5) | live |
| `common/response/` | `ApiResponse<T>` (envelope), `PagedResponse<T>` (first consumed S4.5) | live |
| `common/exception/` | `ErrorCode`, `BusinessException` + concrete subclasses (incl. `PasswordReusedException`, S4.2), `GlobalExceptionHandler` | live |
| `common/audit/` | `AuditAction`, `AuditLog`, `AuditLogRepository`, `AuditService`/`Impl` — synchronous, same-transaction audit writes (S4.1, ADR-017) | live |
| `common/async/` | `AsyncTaskService`/`Impl` — simulated email, activity logging, both `@Async("taskExecutor")` (S4.6) | live |
| `common/util/` | `LogMasking` (S4.6/S4.7) | live |
| `security/` | `JwtService`, `JwtAuthenticationFilter`, `UserPrincipal`, `CustomUserDetailsService`, `AuthController` (login), `security/crypto/AesEncryptionService`, `security/dto/` | live |
| `user/` | `User` entity, `Role`, `UserRepository`, `UserService`/`Impl`, `UserController` (register), `UserMapper`, `user/dto/` | live |
| `vault/` | `Credential` entity (+ `strengthScore`/`passwordChangedAt`, S3.3; `deleted`/`deletedAt` wired up S4.3), `PasswordHistory` entity (S4.2), `Category`, `CredentialRepository` (+ `JpaSpecificationExecutor`, S4.5), `CredentialSpecifications` (S4.5), `PasswordHistoryRepository`, `CredentialService`/`Impl` (create/read/update/soft-delete/restore/trash/permanent-delete/health/history/bulk-recompute), `CredentialController`, `CredentialMapper`, `vault/dto/` | live |
| `password/` | `PasswordStrengthService`/`Impl` (`analyze` cached, S5.3), `PasswordGeneratorService`/`Impl`, `PasswordController` (`/strength`, `/generate`), `password/dto/` (incl. the custom `@AtLeastOneCharacterClass` constraint) — no `Entity`/`Repository`, it's a stateless utility feature, not a persisted one | live |
| `sharing/` | `CredentialShare` entity, `SharePermission`, `AccessLevel`/`AccessEvaluator`/`Impl` (single authorisation decision point, S5.1/ADR-023), `CredentialShareRepository`, `CredentialShareService`/`Impl`, `CredentialShareController`, `CredentialSharedEvent`/`ShareRevokedEvent` (S5.6), `sharing/dto/` | live (S5.1) |
| `security/` (Phase 5 additions) | `RefreshToken` entity + `RefreshTokenRepository`/`Service`/`Impl` (rotation + reuse detection, S5.2/ADR-024), `TokenHasher`, `TokenDenylistService`/`Impl` (Redis, fail-open), `MfaBackupCode` + repo, `MfaService`/`Impl`, `MfaChallengeService`/`Impl` (peek/invalidate, S5.4/ADR-026), `BackupCodeGenerator`, `security/dto/` (MFA + token DTOs) | live (S5.2, S5.4) |
| `monitoring/` | `Device` entity + `DeviceService`/`Impl`/`Controller` (S5.4), `LoginAttempt` + `Service`/`Impl` (brute-force lockout, S5.5/ADR-027), `SecurityAlert`/`AlertType`/`AlertSeverity` + `Service`/`Impl`, `SecurityAlertRaisedEvent`, `VaultAnomalyDetector`/`Impl` (Redis rate counters), `MonitoringController` (login-attempts/alerts/risk-score) | live (S5.4, S5.5) |
| `notification/` | `Notification`/`NotificationType` + `Repository`/`Service`/`Impl`/`Controller`, `EmailService`/`Impl` (`@Async`, MailHog locally), `NotificationEventListener` (`@TransactionalEventListener(AFTER_COMMIT)`, S5.6/ADR-025/ADR-028), `PasswordExpiryCheckService`/`Impl` + `PasswordExpiryScheduler` (`@Scheduled`), `PasswordExpiryWarningEvent` | live (S5.6) |
| `dashboard/` | `DashboardService`/`Impl`/`Controller` — summary/password-health/recent-activity/alerts, database-aggregated, 2-min cache on the first two (S5.7/ADR-029) | live (S5.7) |
| `admin/` | `AdminController` (`@PreAuthorize("hasRole('ADMIN')")`), `AdminStatsService`/`Impl` (cached separately from the auth check, ADR-025), `AdminUserService`/`Impl`, `AdminAuditLogService`/`Impl`, `admin/dto/` (S5.7 stats, S5.8 users/status/audit-logs/ADR-030) | live (S5.7, S5.8) |
| `report/` | reserved (package-info only) | empty, Phase 8+ |

Every feature package with real code follows the same shape: `Entity`, `Repository`
(Spring Data interface), `Service`/`ServiceImpl`, `Controller`, `Mapper` (MapStruct), and a
`dto/` sub-package for request/response records — see `docs/ai/CONVENTIONS.md`.

## Frontend module map (Phase 6, `frontend/src/`)

| Directory | Contains | Status |
|---|---|---|
| `api/` | `client.js` (the one axios instance — request interceptor attaches the bearer token, response interceptor queues concurrent 401s behind a single `/api/auth/refresh` call and retries them, `apiRequest()` unwraps `ApiResponse.data`), `tokenStore.js` (localStorage), one module per backend feature (`auth.js`, `vault.js`, `password.js`, `sharing.js`, `monitoring.js`, `notifications.js`, `dashboard.js`, `admin.js`) | live |
| `app/` | `store.js` (Redux Toolkit, wires `client.js`'s `onAuthExpired` callback to the `sessionExpired` action), `router.jsx` (route-level code splitting via `React.lazy`, `ProtectedRoute`/`AdminRoute` guards) | live |
| `features/auth/` | `authSlice.js` (register/login/MFA-challenge/logout thunks), `registerValidation.js`, `SessionExpiryWarning.jsx` (polls the access token's `exp` claim, S6.8) | live |
| `features/vault/` | `vaultSlice.js` (list/create/update/delete/restore thunks — every mutation re-dispatches the list rather than patching local state, mirroring the backend's own cache-eviction approach), `VaultRow.jsx` (`React.memo`, S6.8), `CredentialFormModal.jsx`, `VaultFilters.jsx`, `useRevealPassword.js` (20s auto-hide, best-effort 30s clipboard clear), `HistoryDrawer.jsx`, `FaviconIcon.jsx`, `categories.js` | live |
| `features/password/` | `GeneratorPanel.jsx`, `PasswordHealthWidget.jsx` (`GET /api/vault/health`) | live |
| `features/sharing/` | `ShareDialog.jsx`, `expiry.js` | live |
| `features/dashboard/` | `SummaryCards.jsx`, `PasswordHealthCard.jsx` (`GET /api/dashboard/password-health` — has `topItemsToFix`, unlike the vault-health endpoint above), `RecentActivity.jsx`, `SecurityAlerts.jsx` (read-only, ADR-034), `CategoryChart.jsx`/`StrengthChart.jsx` (plain markup bars, ADR-033) | live |
| `features/admin/` | `AdminOverview.jsx`, `AdminUsersTable.jsx`, `AdminAuditLogViewer.jsx`, `AdminSecurityMonitoring.jsx` (login-attempts + alerts only — no admin-wide devices endpoint exists, ADR-034) | live |
| `components/` | Base set: `Button`, `Input`, `Card`, `Modal` (focus trap + nested-modal-aware Escape via `modalStack.js`), `Table`, `Badge`, `Spinner`, `Skeleton`, `EmptyState`, `Pagination`, `ConfirmDialog`, `IconButton`, `ScoreDial`, `Tabs`, `ErrorBoundary`, `ProtectedRoute`, `AdminRoute`, `AppLayout` (bottom tab bar below `sm:`, S6.8) | live |
| `pages/` | `LoginPage`, `RegisterPage`, `DashboardPage`, `VaultPage`, `TrashPage`, `SharingPage`, `AdminPage`, `NotFoundPage` | live |
| `hooks/`, `utils/` | `useDebouncedValue`, `apiErrors.js` (backend `errors[]` → field map), `relativeTime.js`, `jwt.js` (client-side `exp` decode, display only) | live |

Forgot/reset-password screens (named in the P6.2 prompt) were **not built** — no backend session
ever implemented `POST /api/auth/forgot-password`/`reset-password`, despite both appearing in
master §11's aspirational "complete target surface." See ADR-034 for this and five smaller
frontend/backend surface gaps found the same way while building Phase 6.

## Async model (P4.6/S4.6)

One shared `ThreadPoolTaskExecutor` bean, `"taskExecutor"` (`config/AsyncConfig`) — corePoolSize
4, maxPoolSize 8, queueCapacity 50, `CallerRunsPolicy`, thread names prefixed `sv-async-`. Two
kinds of work run on it:

- **Generic** (`common/async/AsyncTaskService`): simulated welcome email on registration,
  informational login-activity logging. Neither needs feature-specific data.
- **Feature-specific** (`CredentialService#recomputeStrengthForUser`): bulk password-strength
  recompute, triggered by `POST /api/vault/recompute-strength` (202 Accepted — the work hasn't
  finished when the response returns).

**What must never move onto this executor:** `AuditService.record(...)` (`common/audit/`) —
`@Async` runs in a different transaction than its caller, so an async audit write could never
roll back with the business operation it's meant to describe (P4.1's core requirement). Every
`@Async` method takes any user identity it needs as an explicit parameter, since
`SecurityContextHolder` is empty on the worker thread. `MdcTaskDecorator` (wired into the
executor) is the one piece of context that *does* cross the boundary — the request's correlation
id, so async log lines stay traceable to the request that triggered them. Full reasoning: ADR-020.

## Logging (P4.7/S4.7)

`@Slf4j` (Lombok) throughout — DEBUG for developer/high-frequency detail (a single credential
reveal), INFO for business events (register, login, credential create/update/delete/restore),
WARN for expected-but-notable conditions (every `BusinessException`, failed login attempts —
masked email via `common/util/LogMasking`), ERROR only for the catch-all unexpected case, with
the full stack trace and a correlation id. `config/CorrelationIdFilter` puts one UUID per
request into the SLF4J MDC (honoring an incoming `X-Correlation-Id` header if present) and
echoes it back as a response header; `logback-spring.xml`'s pattern includes `%X{correlationId}`
on every line, console and file both. Console + `RollingFileAppender`
(`SizeAndTimeBasedRollingPolicy`: 10MB or daily, 7-day history, 200MB total cap); `DEBUG` for
`com.securevault` locally, `INFO` in prod, both declared in `logback-spring.xml`'s
`<springProfile>` blocks. `logs/` is gitignored. Full "never logged" list: `docs/decisions.md`
ADR-022.

## Caching (P5.3/S5.3)

Spring Cache backed by Redis (`config/RedisCacheConfig`), three named regions, each with its own
TTL and key prefix (`sv:cache:<region>::`), JSON serialization via a **dedicated** `ObjectMapper`
with `DefaultTyping.EVERYTHING` (not the shared REST mapper, and not `NON_FINAL` — see ADR-025 for
why generics + Java records specifically need `EVERYTHING`):

| Region | TTL | What | Eviction |
|---|---|---|---|
| `vaultList` | 5 min | `GET /api/vault` results, keyed by every filter param | Immediate — every vault create/update/delete/restore does `@CacheEvict(allEntries = true)`; correctness matters more here than per-user precision |
| `passwordStrength` | 10 min | `PasswordStrengthServiceImpl#analyze`, keyed by **SHA-256 hash of the password**, never the password itself | None needed — pure deterministic function of the input |
| `dashboard` | 2 min | Dashboard summary/password-health, admin stats | None — time-based only; documented, accepted staleness (ADR-029) |

Never cached: decrypted credentials, tokens, MFA secrets, or anything an authorization check
gates in the same method (ADR-025 — a cached method's body doesn't re-run on a cache hit, so an
auth check inside it would be bypassed). Cache hit/miss is visible at TRACE on
`org.springframework.cache` (local profile only, `logback-spring.xml`) since Spring's own cache
aspect logs there, not at a level this codebase controls directly.

## Database schema

Full target schema, column-by-column, index rationale, and relationship diagram: `docs/db-design.md`.
ERD source: `docs/erd/securevault.dbml` (paste into dbdiagram.io to render/export — not yet
regenerated for Phase 5's new tables, a standing developer action item since S0.3).
`users`, `credentials`, `audit_logs` (V3), `password_history` (V4), `credential_shares` (V5),
`refresh_tokens` (V6, +`device_fingerprint` V7), `mfa_backup_codes`/`devices` (V7),
`login_attempts`/`security_alerts` (V8), `notifications` (V9) — all live as of Phase 5.

## API index

Full live index (every real endpoint, request/response DTOs, status/error codes) is
`docs/api-contract.md`, regenerated from the actual controllers each phase. As of Phase 5: auth
(register/login/refresh/logout/MFA setup-verify-disable-challenge), full vault CRUD, password
strength/generation, sharing (create/received/sent/update/revoke), monitoring
(devices/login-attempts/alerts/risk-score), notifications, dashboard (summary/password-health/
recent-activity/alerts), admin (stats/users/status/audit-logs), plus Swagger UI — 39 endpoints
total (per the live OpenAPI doc, `GET /v3/api-docs`), disabled in the `prod` profile.

## Testing

**TBD — added in Phase 7.** Target: JUnit 5 + Mockito (unit), Testcontainers PostgreSQL
(integration) per master D-16.

## Deployment

**TBD — added in Phase 8.** Target: Render (backend Docker service + frontend static site),
Neon (PostgreSQL), Upstash (Redis) — see master §18.

## Troubleshooting

- **`mvn spring-boot:run` fails with a missing `JWT_SECRET`/`AES_SECRET_KEY` property** —
  you forgot to `source .env` into the shell before running Maven, or `.env` doesn't have
  those values set. Neither variable has a default on purpose.
- **Flyway fails on startup** — check `docker compose ps`; the `securevault-postgres`
  container must be `healthy` before the backend starts.
- **Port 8080 already in use** — a previous `spring-boot:run` may still be running;
  `pkill -f com.securevault.SecureVaultApplication` (Linux/macOS) or find the process and
  stop it before retrying.
- **Frontend gets CORS errors** — confirm the backend is running with `APP_CORS_ORIGINS`
  covering `http://localhost:5173` (the default) and that `frontend/.env.local`'s
  `VITE_API_BASE_URL` points at the right backend port.
- **Login works but every other request 401s** — check `frontend/.env.local` exists (Vite
  won't read `.env.example`); a missing `VITE_API_BASE_URL` falls back to a relative URL that
  won't reach a backend on a different port.

---
_Last updated: S6.8 — 2026-08-11._
