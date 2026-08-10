# SecureVault — Progress Log

## CURRENT STATE
- Phase: 0 — Workspace & foundations
- Last session: S0.1 (repo, docs system, Docker services, Spring Boot skeleton)
- Build: green | Tests: 0 (none written yet) | Migrations applied: V0 (baseline, no-op)
- Working branch: main (personal fork repo only; no central-repo remote configured yet — see ADR-006)
- Next session: S0.2 — product decomposition + architecture reasoning (M-01, M-02)
- Open blockers: none
- Full phase/milestone tracker: `docs/roadmap.md` (1/53 sessions done)

## NEXT UP
1. S0.2 — product decomposition + architecture reasoning writeups (M-01, M-02)
2. S0.3 — schema design, ERD, Flyway baseline (M-03, M-04, M-05) — real V1__init.sql
3. S1.1 — User entity + registration + BCrypt (M-06, M-07)
4. S1.2 — Spring Security + JWT + login (M-15..M-19)

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
