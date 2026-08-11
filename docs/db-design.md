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

## `audit_logs` — implemented in V3 (S4.1)

| Column | Type | Constraints | Notes |
|---|---|---|---|
| id | BIGSERIAL | PRIMARY KEY | |
| action | VARCHAR(30) | NOT NULL | `AuditAction` enum, `EnumType.STRING` (ADR-011's precedent) |
| entity_type | VARCHAR(50) | NOT NULL | e.g. `"CREDENTIAL"` |
| entity_id | BIGINT | NOT NULL | plain id, not a FK — see below |
| performed_by | BIGINT | NOT NULL | plain id, not a FK — see below |
| timestamp | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |
| ip_address | VARCHAR(45) | nullable | fits IPv6; null when there's no bound HTTP request (e.g. `DevDataSeeder` at startup, S4.5) |
| user_agent | VARCHAR(255) | nullable | same nullability reasoning |
| details | TEXT | nullable | field names / change summary only — never a password, token, or decrypted value (ADR-017, ADR-022) |

**Deliberately no FK** on `entity_id`/`performed_by`, a change from what this section originally
planned in S0.3 (a `performed_by → users(id)` FK) — an audit row must survive permanent deletion
of the entity or user it describes (P4.3's explicit requirement), and no FK means it structurally
cannot be cascade-deleted or blocked by a constraint either way. Full reasoning: ADR-017.
**No delete path ever targets this table** — verified live, not just by inspection: permanent
credential delete leaves `audit_logs` row counts unchanged (`docs/evidence/milestone-2/s4-1-*`).
**Indexes:** `(entity_type, entity_id)` and `performed_by` — both single-column, anticipating a
future audit-browsing endpoint filtering by either.

## `password_history` — implemented in V4 (S4.2)

| Column | Type | Constraints | Notes |
|---|---|---|---|
| id | BIGSERIAL | PRIMARY KEY | |
| credential_id | BIGINT | NOT NULL, FK → `credentials(id)` | **no `ON DELETE CASCADE`** — see below |
| encrypted_password | TEXT | NOT NULL | same `base64(iv):base64(ciphertext)` format as `credentials.encrypted_password` (D-05) — the current ciphertext is copied here as-is on a password change, never re-encrypted |
| version | INT | NOT NULL | starts at 1, increments per password change; immutable once written |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |

**Unique `(credential_id, version)`.** Rows are immutable — never UPDATEd, only INSERTed.
**Deliberately no `ON DELETE CASCADE`**, a change from what this section originally planned in
S0.3 — S4.3's permanent-delete endpoint must delete history explicitly, in application code,
*before* deleting the credential (P4.3's exact sequencing requirement); a plain FK means the
database itself would reject deleting a credential that still has history rows, catching an
ordering mistake instead of silently cascading regardless of order. Full reasoning: ADR-019.
**Index:** `credential_id`, since every real query here (reuse-check top-5, version listing,
permanent-delete cleanup) filters by it.

## `credential_shares` — implemented in V5 (S5.1)

| Column | Type | Constraints | Notes |
|---|---|---|---|
| id | BIGSERIAL | PRIMARY KEY | |
| credential_id | BIGINT | NOT NULL, FK → `credentials(id)` | no `ON DELETE CASCADE` — permanent delete cleans these up explicitly, same reasoning as `password_history` |
| owner_id | BIGINT | NOT NULL, FK → `users(id)` | |
| shared_with_user_id | BIGINT | NOT NULL, FK → `users(id)` | |
| permission | VARCHAR(10) | NOT NULL | `READ`\|`EDIT` |
| shared_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |
| expires_at | TIMESTAMPTZ | nullable | |
| active | BOOLEAN | NOT NULL, DEFAULT true | soft revoke — set false, never deleted |

**Unique** `(credential_id, shared_with_user_id)` **filtered `WHERE active`** — enforces "no
duplicate active share" (M-45) without blocking re-sharing after a revoke, since the partial
index simply ignores inactive rows. **Indexes:** `shared_with_user_id`, `credential_id`,
`owner_id` — one per direction the sharing feature queries from (received/sent/cleanup).

## `refresh_tokens` — implemented in V6 (S5.2) + V7 (S5.4)

| Column | Type | Constraints | Notes |
|---|---|---|---|
| id | BIGSERIAL | PRIMARY KEY | |
| user_id | BIGINT | NOT NULL, FK → `users(id)` | |
| token_hash | VARCHAR(64) | NOT NULL, UNIQUE | SHA-256 hex of the raw token — the token itself is never stored, same principle as `password_hash` |
| token_family | VARCHAR(36) | NOT NULL | UUID shared by every token descended from one login; reuse detection revokes the whole family in one statement |
| device_fingerprint | VARCHAR(64) | nullable, **added in V7** | lets `DELETE /api/monitoring/devices/{id}` revoke exactly that device's sessions |
| expires_at | TIMESTAMPTZ | NOT NULL | |
| revoked | BOOLEAN | NOT NULL, DEFAULT false | |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |

Redis holds the fast access-token denylist (`jwt:denylist:<jti>`, TTL = remaining token
lifetime); this table is the durable refresh-token record. **Indexes:** `user_id`,
`token_family`.

## `mfa_backup_codes` and `devices` — implemented in V7 (S5.4)

**`mfa_backup_codes`**: `id` BIGSERIAL PK · `user_id` BIGINT FK → `users(id)` · `code_hash`
VARCHAR(60) NOT NULL (BCrypt, same treatment as account passwords) · `used` BOOLEAN NOT NULL
DEFAULT false · `created_at` TIMESTAMPTZ NOT NULL. Index: `user_id`.

**`devices`**: `id` BIGSERIAL PK · `user_id` BIGINT FK → `users(id)` · `device_fingerprint`
VARCHAR(64) NOT NULL · `device_name` VARCHAR(150) · `ip_address` VARCHAR(45) · `user_agent`
VARCHAR(255) · `last_seen_at` TIMESTAMPTZ NOT NULL · `trusted` BOOLEAN NOT NULL DEFAULT true ·
`created_at` TIMESTAMPTZ NOT NULL. **Unique** `(user_id, device_fingerprint)` — one row per
device per user, upserted on every login from that device.

## `login_attempts` and `security_alerts` — implemented in V8 (S5.5)

**`login_attempts`**: `id` BIGSERIAL PK · `email` VARCHAR(150) NOT NULL (kept even when it
doesn't resolve to a user — the attempt itself is real regardless) · `successful` BOOLEAN
NOT NULL · `ip_address` VARCHAR(45) · `user_agent` VARCHAR(255) · `attempted_at` TIMESTAMPTZ
NOT NULL · `failure_reason` VARCHAR(100). Indexes: `email`, `attempted_at`.

**`security_alerts`**: `id` BIGSERIAL PK · `user_id` BIGINT FK → `users(id)` · `type`
VARCHAR(50) NOT NULL (`NEW_DEVICE`, `ELEVATED_FAILED_ATTEMPTS`, `BRUTE_FORCE_LOCKOUT`,
`EXCESSIVE_VAULT_ACCESS`, `MASS_PERMANENT_DELETE`) · `severity` VARCHAR(20) NOT NULL
(`LOW`\|`MEDIUM`\|`HIGH`) · `message` VARCHAR(500) NOT NULL · `resolved` BOOLEAN NOT NULL
DEFAULT false · `created_at` TIMESTAMPTZ NOT NULL. Index: `user_id`.

No dedicated `locked_at` column on `users` — the 30-minute auto-unlock (P5.5) is instead
derived from `login_attempts`'s most recent failure timestamp for that email
(`CustomUserDetailsService`), avoiding a column whose only purpose would be duplicating
information `login_attempts` already has.

## `notifications` — implemented in V9 (S5.6)

`id` BIGSERIAL PK · `user_id` BIGINT FK → `users(id)` · `type` VARCHAR(50) NOT NULL
(`NEW_DEVICE_LOGIN`, `SECURITY_ALERT`, `CREDENTIAL_SHARED`, `SHARE_REVOKED`,
`PASSWORD_EXPIRY`) · `title` VARCHAR(200) NOT NULL · `message` VARCHAR(500) NOT NULL ·
`read` BOOLEAN NOT NULL DEFAULT false · `created_at` TIMESTAMPTZ NOT NULL. Index: `user_id`.
Populated exclusively by `NotificationEventListener`
(`@TransactionalEventListener(phase = AFTER_COMMIT)`) — never written directly by the code
that raises the underlying event (ADR-025).

> Column types not pinned by master §10 (e.g. `ip_address`, `user_agent`, `device_fingerprint`,
> `token_hash` lengths) were this session's own reasonable engineering choices, confirmed
> workable by every Phase 5 migration applying and every live test passing against them.

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
User 1 ──< MfaBackupCode
User 1 ──< LoginAttempt (by email, no FK)
User 1 ──< SecurityAlert
Credential 1 ──< CredentialShare >── 1 User (shared_with)
```

Full DBML source lives in `docs/erd/securevault.dbml` — not yet regenerated for Phase 5's four
new tables (`credential_shares`, `refresh_tokens`, `mfa_backup_codes`, `devices`,
`login_attempts`, `security_alerts`, `notifications`); the manual dbdiagram.io export step below
was already outstanding since S0.3 and remains a developer action item, now covering nine
additional tables' worth of relations rather than the original two.

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
_Last updated: S5.8 — 2026-08-11._
