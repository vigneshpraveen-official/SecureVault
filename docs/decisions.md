# SecureVault — Architecture Decision Records

Append-only. Never edit an accepted ADR — add a new one that supersedes it and say so.
Format: Date · Status · Context · Decision · Alternatives · Consequences.

---

### ADR-001 — Java 21 + Spring Boot 3.5.x baseline
**Date:** 2026-08-10 · **Status:** accepted
**Context:** `docs/securevault_master.md` D-01/D-02 lock Java 21 (LTS) and "Spring Boot 3.3.x, current stable line". As of this session, Spring Boot 3.3.x is fully EOL (final release 3.3.13, June 2025) and even the newer 3.5.x line reached EOL in June 2026; current is Spring Boot 4.0/4.1.
**Decision:** Java 21 unchanged. Pin the backend to **Spring Boot 3.5.16** (last release of the 3.5.x line) instead of literal 3.3.x. 3.5.x shares the same Jakarta EE / Spring Security 6 API surface as 3.3.x, so nothing else in the master document (JWT filter chain shape, D-08 jjwt guidance, etc.) needs to change.
**Alternatives:** Spring Boot 3.3.13 exactly (matches D-02 literally, but is a full year further from any patches); Spring Boot 4.0.6/4.1.0 (actively maintained, but introduces Spring Security 7 and other breaking changes the master document does not account for — higher risk of mismatched guidance mid-project). User chose 3.5.16 when asked directly.
**Consequences:** D-02 is amended by this ADR — read "Spring Boot 3.3.x" in the master document as "Spring Boot 3.5.x" going forward. No known API divergence expected for this project's scope.

### ADR-002 — Monolith, feature-first packages over microservices
**Date:** 2026-08-10 · **Status:** accepted
**Context:** The internship PDF's architecture poster implies a larger, possibly distributed system, but the mentor grades layering and correctness, not distributed-systems operations, and the project runs at zero budget.
**Decision:** Single Spring Boot module with feature-first packages (`user`, `vault`, `password`, `sharing`, `notification`, `monitoring`, `report`, `admin`, plus `config`/`common`/`security`) that mirror microservice boundaries without the operational cost.
**Alternatives:** Microservice decomposition (rejected — Track C, out of scope, no budget for orchestration); layer-first packages (`controllers/`, `services/`, `repositories/`) (rejected — harder to reason about ownership per feature, worse for a mentor reviewing one capability at a time).
**Consequences:** Package boundaries double as the seams for a future extraction, but none is planned. Cross-feature shortcuts are a review-time rejection per master §9.

### ADR-003 — Flyway migrations from day 1, `ddl-auto=validate`
**Date:** 2026-08-10 · **Status:** accepted
**Context:** The project is developed across multiple AI agents and possibly multiple machines. `ddl-auto=update` lets Hibernate silently mutate the schema, which diverges between environments and is undetectable until it breaks something.
**Decision:** Flyway owns every schema change (`V<n>__<description>.sql`, `flyway.clean-disabled=true`); Hibernate is set to `ddl-auto=validate` and only checks entities against the migrated schema. This session ships `V0__baseline.sql` — a no-op — because no entities exist yet; the real schema arrives as `V1__init.sql` in S0.3.
**Alternatives:** `ddl-auto=update` (rejected — the #1 source of "works on my machine" per master §6); `ddl-auto=none` with hand-run SQL (rejected — no safety net, no reproducibility).
**Consequences:** Every future entity change requires a matching migration file before the app will start. `flyway-database-postgresql` is required alongside `flyway-core` on Flyway 10+ (already added to `pom.xml`).

### ADR-004 — AES-256-GCM for vault secrets
**Date:** 2026-08-10 · **Status:** accepted
**Context:** Vault credentials (unlike account passwords) must be recoverable in plaintext for the user, so a one-way hash (BCrypt) cannot be used. The mentor may specifically probe for insecure modes (ECB, CBC without a MAC).
**Decision:** AES-256-GCM, a random 12-byte IV generated per record, stored as `base64(iv):base64(ciphertext)` in a single `TEXT` column (`encrypted_password`). The master AES key is 32 bytes, base64-encoded, read only from the `AES_SECRET_KEY` environment variable — never committed.
**Alternatives:** AES-CBC without a MAC (rejected — no tamper detection, a known security bug class); ECB (rejected — pattern leakage, insecure by construction); a KMS-managed key (rejected — no budget, out of scope for an 8-week zero-budget internship project).
**Consequences:** Key rotation requires a re-encryption migration (must be planned, never improvised — master §19 risk register). Implementation lands in S1.3 (`AesEncryptionService`); this ADR only locks the algorithm and storage format ahead of time.

### ADR-005 — `V0__baseline.sql` as a deliberate no-op migration
**Date:** 2026-08-10 · **Status:** accepted
**Context:** `ddl-auto=validate` (ADR-003) requires Hibernate to validate every entity against the live schema, but no entities exist yet in S0.1. Flyway also needs at least one migration to establish its history table before the app can safely rely on it later.
**Decision:** Ship `backend/src/main/resources/db/migration/V0__baseline.sql` containing only a comment and `SELECT 1;` — no schema changes. This was chosen over the alternative of temporarily setting `ddl-auto=none`.
**Alternatives:** `ddl-auto=none` with a `TODO` for S0.3 (rejected — leaves the app running without the safety net ADR-003 exists for, even briefly); skip Flyway entirely until S0.3 (rejected — `flyway.enabled=true` is a locked default per master §13, and starting Flyway from day 1 exercises the whole migration path before real schema risk is on the line).
**Consequences:** `V1__init.sql` (S0.3) becomes the first real schema migration. `V0` must never be edited once applied — future changes are always new files.

### ADR-006 — Fork-only single-repository workflow (temporary)
**Date:** 2026-08-10 · **Status:** accepted, revisit before S9.1
**Context:** Master §7.1 assumes two repositories (a personal dev repo plus the central mentor repo, with a `sync-submission.sh` script bridging them at submission time). The developer is working entirely from a GitHub fork of the central repo (`github.com/vigneshpraveen-official/SecureVault`) and the mentor has not yet given instructions about pushing or merging to the upstream/central repo.
**Decision:** Treat the fork as the sole working repository for now. `git remote origin` points at the fork; no `central` remote is configured. `scripts/sync-submission.sh` is still created (per master §7.3) but is not run this session and will need its `central` remote added once the mentor gives submission instructions.
**Alternatives:** Configure the `central` remote now anyway (rejected — no confirmed push access or mentor instruction yet, and adding it prematurely risks an accidental push to a shared repo); abandon the two-repo model in the master document entirely (rejected — §7.1's roles still make sense once submission instructions arrive, this ADR only defers the second remote).
**Consequences:** `docs/progress.md` CURRENT STATE reflects "no central remote configured" until this ADR is superseded. Revisit at S9.1 (central-repo sync) or sooner if the mentor gives branch/push instructions.

### ADR-007 — Spotless + Google Java Format (AOSP style) as the mechanical formatter
**Date:** 2026-08-10 · **Status:** accepted
**Context:** Multiple AI agents (Claude Code, Gemini, ChatGPT, Antigravity) will touch this codebase and each formats Java slightly differently by default, which shows up as unrelated noise in diffs.
**Decision:** `spotless-maven-plugin` bound to the `verify` Maven phase, using Google Java Format in **AOSP style** (4-space indent) so it matches the repo's `.editorconfig`. `mvn spotless:apply` formats; `mvn verify` fails the build on drift.
**Alternatives:** `fmt-maven-plugin` (rejected — smaller community, fewer style options); default Google Java Format (2-space) (rejected — conflicts with the repo's 4-space `.editorconfig` and master §9's Java conventions).
**Consequences:** Every agent must run `mvn spotless:apply` before committing Java changes, or let `mvn verify` catch it. No agent may hand-argue about brace placement or import order in review.
