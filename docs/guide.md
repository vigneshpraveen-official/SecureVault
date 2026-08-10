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

Full target request flow (DispatcherServlet → Controller → Service → Repository → Hibernate
→ PostgreSQL) — **TBD, added once the first real endpoint (S1.1) exists.**

## Module map

**TBD — added in S1.1** once `user/` has real classes to describe.

## Database schema

**TBD — added in S0.3.** See `docs/db-design.md` (currently a stub) and master §10 for the
target model.

## API index

**TBD — grows with `docs/api-contract.md`.** Currently only `GET /actuator/health` exists.

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
_Last updated: S0.1 — 2026-08-10. All commands above were run and verified this session._
