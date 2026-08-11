# SecureVault — Database Design (M-03, M-04, M-05)

Session S0.3. Full target schema from `docs/securevault_master.md` §10, documented ahead of
implementation. **Only `users` and `credentials` are implemented in this session's migration**
(`V1__init.sql`) — Phase 1 needs exactly those two. Every other table below is documented now
so the shape is agreed in advance, but is created by its own Flyway migration in the session
that actually needs it (never bundled ahead of time — see `docs/decisions.md` ADR-003).

Connection used to verify this session: `localhost:5432`, database `securevault`, user
`postgres` (Docker container `securevault-postgres`, started in S0.1). Password read from
`.env`'s `DB_PASSWORD`, never printed.

---

## `users` — implemented in V1

| Column | Type | Constraints | Notes |
|---|---|---|---|
| id | BIGSERIAL | PRIMARY KEY | |
| full_name | VARCHAR(100) | NOT NULL | |
| email | VARCHAR(150) | NOT NULL, UNIQUE | login identifier; unique constraint auto-creates a btree index |
| password_hash | VARCHAR(60) | NOT NULL | BCrypt output is always 60 chars |
| role | VARCHAR(20) | NOT NULL | `USER`, `TEAM_MEMBER`, `ADMIN` |
| mfa_enabled | BOOLEAN | NOT NULL, DEFAULT false | |
| mfa_secret | VARCHAR(255) | nullable | encrypted TOTP secret, set when MFA is enabled (Phase 5) |
| account_locked | BOOLEAN | NOT NULL, DEFAULT false | set by the failed-login lockout policy (Phase 5) |
| failed_login_attempts | INT | NOT NULL, DEFAULT 0 | reset to 0 on successful login |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |
| updated_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | app/trigger-maintained on update, not yet automated in V1 |

**Primary key:** `id`. **Unique:** `email`. **Indexes:** the unique constraint on `email` is
the only index this table needs — every login and duplicate-check query goes through it.

## `credentials` — implemented in V1 + V2

| Column | Type | Constraints | Notes |
|---|---|---|---|
| id | BIGSERIAL | PRIMARY KEY | |
| user_id | BIGINT | NOT NULL, FK → `users(id)` | owner; LAZY on the JPA side (Phase 1) |
| title | VARCHAR(150) | NOT NULL | **indexed** |
| username | VARCHAR(150) | nullable | the login username/email for this credential, not the SecureVault account |
| encrypted_password | TEXT | NOT NULL | `base64(iv):base64(ciphertext)` — never plaintext (D-05) |
| website_url | VARCHAR(255) | nullable | |
| notes | TEXT | nullable | encrypted at the application layer if sensitive (Phase 1+) |
| category | VARCHAR(30) | NOT NULL | enum value; **indexed** |
| favorite | BOOLEAN | NOT NULL, DEFAULT false | |
| strength_score | SMALLINT | nullable | cached `PasswordStrengthService` result (0-5), set at create and whenever the password actually changes — **mapped as of S3.3** (existed unmapped since V1) |
| password_changed_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | **added in V2 (S3.3)** — set at create, updated *only* when the password itself changes, never on a title/category/etc. edit; deliberately separate from `updated_at` for that reason. Existing pre-V2 rows were backfilled to `now()` at migration time (their true original change date isn't recoverable — disclosed baseline, see ADR) |
| deleted | BOOLEAN | NOT NULL, DEFAULT false | soft delete (Phase 4) |
| deleted_at | TIMESTAMPTZ | nullable | set when `deleted` flips true |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |
| updated_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |

**Primary key:** `id`. **Foreign key:** `user_id → users(id)`. **Indexes:** `title`,
`category`, composite `(user_id, deleted)` — see rationale below.

---

## Tables documented now, migrated later

### `password_history` (Phase 4, S4.2)
`id` BIGSERIAL PK · `credential_id` BIGINT FK → `credentials(id)` ON DELETE CASCADE ·
`encrypted_password` TEXT NOT NULL · `version` INT NOT NULL · `created_at` TIMESTAMPTZ NOT NULL.
Unique `(credential_id, version)`. Rows are immutable — never UPDATEd, only INSERTed.

### `credential_shares` (Phase 5, S5.1)
`id` BIGSERIAL PK · `credential_id` BIGINT FK → `credentials(id)` ·
`owner_id` BIGINT FK → `users(id)` · `shared_with_user_id` BIGINT FK → `users(id)` ·
`permission` VARCHAR(10) NOT NULL (`READ`\|`EDIT`) · `shared_at` TIMESTAMPTZ NOT NULL ·
`expires_at` TIMESTAMPTZ nullable · `active` BOOLEAN NOT NULL DEFAULT true.
Unique `(credential_id, shared_with_user_id)` filtered `WHERE active` — enforces "no duplicate
active share" without blocking re-sharing after a revoke.

### `audit_logs` (Phase 4, S4.1)
`id` BIGSERIAL PK · `action` VARCHAR(50) NOT NULL · `entity_type` VARCHAR(50) NOT NULL ·
`entity_id` BIGINT nullable · `performed_by` BIGINT FK → `users(id)` nullable (nullable so a
failed pre-auth action, e.g. a failed login, can still be logged) · `timestamp` TIMESTAMPTZ
NOT NULL · `ip_address` VARCHAR(45) (fits IPv6) · `user_agent` VARCHAR(255) · `details` TEXT.
**No delete path ever targets this table** — not even permanent credential delete (M-38).

### `login_attempts` (Phase 5, S5.5)
`id` BIGSERIAL PK · `email` VARCHAR(150) NOT NULL (kept even if no matching user, to detect
enumeration attempts) · `successful` BOOLEAN NOT NULL · `ip_address` VARCHAR(45) ·
`user_agent` VARCHAR(255) · `attempted_at` TIMESTAMPTZ NOT NULL · `failure_reason` VARCHAR(255).

### `devices` (Phase 5, S5.4)
`id` BIGSERIAL PK · `user_id` BIGINT FK → `users(id)` · `device_fingerprint` VARCHAR(255)
NOT NULL · `device_name` VARCHAR(150) · `ip_address` VARCHAR(45) · `last_seen_at` TIMESTAMPTZ ·
`trusted` BOOLEAN NOT NULL DEFAULT false.

### `refresh_tokens` (Phase 5, S5.2)
`id` BIGSERIAL PK · `user_id` BIGINT FK → `users(id)` · `token_hash` VARCHAR(255) NOT NULL
(the token itself is never stored, only its hash — same reasoning as account passwords) ·
`expires_at` TIMESTAMPTZ NOT NULL · `revoked` BOOLEAN NOT NULL DEFAULT false ·
`created_at` TIMESTAMPTZ NOT NULL. Redis holds the fast denylist; this table is the durable
record (`docs/architecture.md`).

### `notifications` (Phase 5, S5.6)
`id` BIGSERIAL PK · `user_id` BIGINT FK → `users(id)` · `type` VARCHAR(50) NOT NULL ·
`title` VARCHAR(150) NOT NULL · `message` TEXT · `read` BOOLEAN NOT NULL DEFAULT false ·
`created_at` TIMESTAMPTZ NOT NULL.

> Column types not pinned by master §10 (e.g. `ip_address`, `user_agent`, `device_fingerprint`,
> `token_hash` lengths) are this session's own reasonable engineering choices, not mentor
> requirements — flagged here so a later session can revisit them deliberately if needed.

---

## Index rationale (answers M-23 in advance)

- **`title` (btree, single column):** every list/search request the mentor grades (M-21) does
  a partial-title match. Without an index this is a sequential scan that gets linearly slower
  as a user's vault grows toward the ≥50-row seed required for pagination (M-34).
- **`category` (btree, single column):** the category filter (M-22) is a plain equality check
  against one of 7 enum values. Even with low cardinality, an index here avoids scanning every
  row across every user just to find the ones in one category, and composes with the `user_id`
  filter via a bitmap index scan when both are present in a query.
- **`(user_id, deleted)` (composite, leading column `user_id`):** this is the single most
  common predicate in the entire vault module — list, get-by-id, search, and category filter
  all scope to "this user's rows" and, from Phase 4 onward, "that are not soft-deleted"
  (M-39). Postgres does **not** automatically index foreign key columns, so without this index
  every one of those queries would scan the whole `credentials` table regardless of how many
  other users' rows are mixed in. Leading with `user_id` also means the index alone serves any
  query that filters on `user_id` without needing `deleted` in the predicate.
- **Write-cost tradeoff:** three indexes on `credentials` means every `INSERT`/`UPDATE` that
  touches `title`, `category`, `user_id`, or `deleted` pays B-tree maintenance cost on top of
  the row write. A password vault is read-heavy and write-light by nature — users list and
  search constantly, but create/update a credential comparatively rarely — so trading a small,
  constant write cost for consistently fast reads is the right tradeoff here.
- **Which queries each index serves:**
  - `idx_credentials_title` → `GET /api/vault/search?q=` (M-21)
  - `idx_credentials_category` → `GET /api/vault?category=BANKING` (M-22)
  - `idx_credentials_user_id_deleted` → `GET /api/vault` (list), `GET /api/vault/{id}`,
    search, and category filter — all of them, since every one of those queries scopes by
    owner and (from Phase 4) excludes soft-deleted rows.

**Honest caveat added in S1.5:** `idx_credentials_title` is a standard btree index, which only
accelerates *prefix* matches (`LIKE 'term%'`). S1.5's search (M-21) is a *contains* match
(`LIKE '%term%'`, case-insensitive, across `title`/`username`/`websiteUrl`) so it can't use a
leading-wildcard lookup on this index — Postgres falls back to a scan filtered by `user_id`
first (via `idx_credentials_user_id_deleted`), which is fine at this project's data volume but
won't scale indefinitely. A trigram index (`pg_trgm` extension, `gin_trgm_ops`) would make
substring search itself index-accelerated; not added now since it's a new extension/dependency
decision out of this session's scope — flagged here for a future session if search performance
ever becomes a real bottleneck.

---

## Relationship summary

```
User 1 ──< Credential 1 ──< PasswordHistory
User 1 ──< AuditLog
User 1 ──< Device
User 1 ──< Notification
User 1 ──< RefreshToken
Credential 1 ──< CredentialShare >── 1 User (shared_with)
```

Full DBML source (all 9 tables, every relation) lives in `docs/erd/securevault.dbml`.

### Exporting the ERD image from dbdiagram.io

1. Go to https://dbdiagram.io and start a new diagram (or "Import" if it offers a DBML import option).
2. Open `docs/erd/securevault.dbml` in this repo, copy its full contents.
3. Paste into dbdiagram.io's editor pane — the diagram renders on the right automatically.
4. Use the **Export** menu (top toolbar) → **Export to PNG** (or SVG, if you'd prefer a
   scalable image for the presentation deck later).
5. Save the downloaded file as `docs/erd/securevault.png` in this repo.
6. `git add docs/erd/securevault.png` next session (or now, if you export it right away) —
   it isn't committed yet since I can't reach the dbdiagram.io web UI myself.

---
_Session S0.3 — 2026-08-11._
