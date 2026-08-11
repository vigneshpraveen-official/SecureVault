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

## Architecture overview

```
Browser
  │
  ▼
React 18 + Vite (frontend/ — not built yet, Phase 6)
  │  HTTPS /api/**
  ▼
Spring Boot 3.5 (backend/) — monolith, feature-first packages
  ├── config/      Security, Async, Redis, OpenAPI, CORS
  ├── common/      response envelope, exceptions, audit, util
  ├── security/    JWT service/filter, AES-GCM crypto
  ├── user/ vault/ password/ sharing/ notification/ monitoring/ report/ admin/
  ▼
PostgreSQL 16 (Docker locally / Neon in prod) — Flyway-migrated, ddl-auto=validate
Redis 7 (Docker locally / Upstash in prod) — JWT denylist + vault-list cache (from Phase 5)
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
| `password/` | `PasswordStrengthService`/`Impl`, `PasswordGeneratorService`/`Impl`, `PasswordController` (`/strength`, `/generate`), `password/dto/` (incl. the custom `@AtLeastOneCharacterClass` constraint) — no `Entity`/`Repository`, it's a stateless utility feature, not a persisted one | live |
| `sharing/`, `notification/`, `monitoring/`, `report/`, `admin/` | reserved (package-info only) | empty, Phase 5+ |

Every feature package with real code follows the same shape: `Entity`, `Repository`
(Spring Data interface), `Service`/`ServiceImpl`, `Controller`, `Mapper` (MapStruct), and a
`dto/` sub-package for request/response records — see `docs/ai/CONVENTIONS.md`.

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

## Database schema

Full target schema, column-by-column, index rationale, and relationship diagram: `docs/db-design.md`.
ERD source: `docs/erd/securevault.dbml` (paste into dbdiagram.io to render/export).
`users`, `credentials`, `audit_logs` (V3, S4.1), and `password_history` (V4, S4.2) exist as of
Phase 4 — everything else is documented ahead of time and migrated in the phase that needs it.

## API index

Full live index (every real endpoint, request/response DTOs, status/error codes) is
`docs/api-contract.md`, regenerated from the actual controllers each phase. As of Phase 4:
register, login, password strength/generation, and full vault CRUD + paginated
list/search/filter/health/trash/restore/permanent-delete/history/bulk-recompute —
16 endpoints plus `/actuator/health`.

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

---
_Last updated: S4.8 — 2026-08-11._
