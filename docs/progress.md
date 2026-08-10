# SecureVault — Progress Log

## CURRENT STATE
- Phase: 0 — Workspace & foundations (complete after this session)
- Last session: S0.3 (schema design, ERD, Flyway baseline)
- Build: green | Tests: 0 | Migrations applied: V0, V1 (users, credentials)
- Working branch: main (personal fork repo only; no central-repo remote configured yet — see ADR-006)
- Next session: S1.1 — User entity + registration + BCrypt (M-06, M-07)
- Open blockers: ERD PNG export is a manual dbdiagram.io step, not yet done by the developer (DBML source is committed)
- Full phase/milestone tracker: `docs/roadmap.md` (3/53 sessions done)

## NEXT UP
1. S1.1 — User entity + registration + BCrypt (M-06, M-07)
2. S1.2 — Spring Security + JWT + login (M-15..M-19)
3. S1.3 — Credential entity + AES-GCM + create/read (M-08, M-09, M-10)
4. S1.4 — Update, delete, ownership verification (M-11, M-12, M-13)

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
