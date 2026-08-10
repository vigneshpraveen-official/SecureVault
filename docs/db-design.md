# SecureVault — Database Design

**Status: stub.** The full schema, ER diagram, and index rationale land in session **S0.3**
(mentor tasks M-03/M-04/M-05), which also delivers the real `V1__init.sql` Flyway migration.
As of S0.1 the `securevault` database exists (Docker, PostgreSQL 16) with **no tables** —
only Flyway's own `flyway_schema_history`, created by the deliberate no-op
`V0__baseline.sql` (see `docs/decisions.md` ADR-005).

The target model is already fully specified in `docs/securevault_master.md` §10 — this file
will restate it here, table by table, with the reasoning the mentor asks for once S0.3 runs.

## Sections to fill in S0.3

### Entity-relationship diagram
_TBD — added in S0.3. Exported to `docs/erd/` (dbdiagram.io / draw.io / Excalidraw source + image)._

### Tables
_TBD — added in S0.3. One subsection per table: columns, types, keys, constraints._

- `users`
- `credentials`
- `password_history`
- `credential_shares`
- `audit_logs`
- `login_attempts`
- `devices`
- `refresh_tokens`
- `notifications`

### Index rationale
_TBD — added in S0.3._ Answers the M-23 "why was this indexed" requirement so the explanation
lives in docs (per master §5.1's README-conflict resolution) instead of a root `README.md`.

### Relationship summary
_TBD — added in S0.3._ Mirrors master §10's relationship diagram once the migration exists.

---
_Last updated: S0.1 — 2026-08-10._
