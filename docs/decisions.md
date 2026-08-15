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

### ADR-008 — Commit cadence: one commit per phase, not per session
**Date:** 2026-08-11 · **Status:** accepted
**Context:** Master §7.4/§14 step 6 specify one commit per session. The developer has instead
asked for commits only at phase completion, not after every session or sub-phase change,
to keep the git history focused on phase-sized units of work rather than many small
scaffolding/doc commits.
**Decision:** Sessions within a phase are still executed individually (plan stated, work
done, `docs/progress.md`/`docs/roadmap.md` updated live) but are **not** committed
individually. One commit happens at the end of the phase, covering every session in it.
Work stays on `main` — no phase branches are introduced (would add squash-merge overhead
not requested, and conflicts with the already-simplified fork-only workflow in ADR-006).
Commit approval is still asked for explicitly every time a commit does happen — this
changes *frequency*, not the requirement to ask.
**Alternatives:** Master §7.2's literal `phase/<n>-<slug>` branch + squash-merge model
(rejected for now — adds branch management overhead disproportionate to a solo-developer
fork-only workflow; revisit if that changes); keep per-session commits (rejected — developer's
explicit preference is a phase-sized history).
**Consequences:** `docs/progress.md`'s SESSION LOG still gets one entry per session (for
traceability), but several entries may land in a single commit. If a phase is interrupted
mid-way, uncommitted work for completed sessions within that phase exists only in the
working tree until the phase finishes or the developer asks for an interim commit.

### ADR-009 — jjwt 0.12.7 for JWT issuance/validation
**Date:** 2026-08-11 · **Status:** accepted
**Context:** D-08 locks "jjwt 0.12.x (HS256)" for JWT. No JWT dependency existed yet (S1.1's
`SecurityConfig` was a permit-all placeholder). Unlike the Spring Boot situation (ADR-001),
0.12.x is not EOL — it's simply superseded by a newer 0.13.0 that D-08 doesn't ask for.
**Decision:** Add `io.jsonwebtoken:jjwt-api`, `jjwt-impl` (runtime), `jjwt-jackson` (runtime),
all pinned to **0.12.7** (latest 0.12.x patch, verified via Maven Central). Used exclusively
via the 0.12 API style (`Jwts.builder().subject(...).signWith(SecretKey)`,
`Jwts.parser().verifyWith(...)`) — the deprecated 0.9-era API never appears in this codebase.
**Alternatives:** jjwt 0.13.0 (rejected — D-08 explicitly asks for the 0.12.x line; no
functional need to jump to 0.13 yet); Spring Security's own OAuth2 resource server JWT
support (rejected — heavier setup for a project issuing its own tokens rather than
validating a third-party IdP's).
**Consequences:** Any future session touching JWT must stay on the 0.12 builder/parser API.
If the project later needs 0.13+, that requires its own ADR.

### ADR-010 — AesEncryptionService implementation details (fail-fast key validation)
**Date:** 2026-08-11 · **Status:** accepted
**Context:** ADR-004 already locked the algorithm and storage format (`base64(iv):base64(ciphertext)`)
ahead of implementation. This session (S1.3) implements it — the P1.3 prompt asks for an ADR
on the ciphertext format, which ADR-004 already covers, so this entry only records the
implementation-specific decisions ADR-004 didn't: exact tag length and key-validation behavior.
**Decision:** `Cipher.getInstance("AES/GCM/NoPadding")`, 128-bit GCM auth tag, `SecureRandom`
for the 12-byte IV (matches D-05 exactly). `AES_SECRET_KEY` is decoded and length-checked in
the service's constructor — Spring fails the whole application context at startup with a
clear message if the key is missing, not valid base64, or doesn't decode to exactly 32 bytes.
**Alternatives:** Validating lazily on first `encrypt()`/`decrypt()` call (rejected — a bad key
would only surface on the first real request instead of at boot, the opposite of "fail fast");
a fixed/zero IV (rejected outright — defeats GCM's entire security property, this is the
insecure pattern D-05's rationale explicitly warns against).
**Consequences:** The app will not start at all with a missing/malformed `AES_SECRET_KEY` —
this is intentional per master §13 ("no defaults for ... AES_SECRET_KEY").

### ADR-011 — Category persisted as `EnumType.STRING`, never `ORDINAL`
**Date:** 2026-08-11 · **Status:** accepted
**Context:** JPA can persist an enum as its declared name (`STRING`) or its declaration-order
integer position (`ORDINAL`). `Credential.category` (S1.3/S1.5) needed one chosen explicitly.
**Decision:** `@Enumerated(EnumType.STRING)`. The DB column is `VARCHAR(30)` (`V1__init.sql`),
which is also the only type-compatible choice — `ORDINAL` would need an `INT` column.
**Alternatives:** `ORDINAL` (rejected — it's a silent-corruption risk, not just a style
preference: if `Category`'s declared order ever changes — a new value inserted in the middle,
a reorder for readability — every already-stored integer silently points at a *different*
enum constant. There's no compile error and no runtime error; existing rows just start
reporting the wrong category. `STRING` fails loudly instead: renaming a constant breaks
lookups visibly rather than corrupting data invisibly).
**Consequences:** Renaming a `Category` constant later requires an explicit data migration
(`UPDATE credentials SET category = 'NEW_NAME' WHERE category = 'OLD_NAME'`) — a deliberate,
visible action, which is exactly the tradeoff this ADR accepts in exchange for never having
silent data corruption from a reordered enum.

### ADR-012 — MapStruct for DTO↔Entity mapping (D-10), records as DTOs, split list/detail responses
**Date:** 2026-08-11 · **Status:** accepted
**Context:** S1.1-S1.6 built DTO↔Entity conversion as hand-written static `from(...)` factory
methods on each response record. D-10 already locks MapStruct as the intended tool; P2.1/M-24
asks the intern to demonstrate understanding of *why* mapping exists at all, not just wire up a
library.
**Decision:** Introduce `UserMapper` and `CredentialMapper` (`@Mapper(componentModel = "spring")`),
generated at compile time via `mapstruct-processor`, with `lombok-mapstruct-binding` on the
annotation processor path so Lombok's generated getters/setters/builder are visible to MapStruct
during the same compilation pass (without it, MapStruct sees no accessors and silently emits
empty mapper bodies — the exact trap master §20's known-issues table warns about). Any field
needing encryption/decryption (`encryptedPassword` ↔ plaintext `password`) is explicitly excluded
from every mapper method (`@Mapping(target = ..., ignore = true)`) and set in the service instead
— mappers stay free of business logic and crypto, per the P2.1 prompt's explicit instruction.
DTOs stay Java records (immutable, no setters, MapStruct 1.6.x supports records as mapping
targets via constructor-parameter matching). `CredentialResponse` (create/update) and
`CredentialSummaryResponse` (list/search) are now two distinct record types with identical
fields today, so the list contract can evolve independently of the single-resource contract
later without a breaking change — the API-contract-stability argument the P2.1 prompt asks for.
Reasons mapping exists at all, for the record: (1) API contract stability — an entity's shape is
free to change without breaking every response; (2) over-posting prevention — a request DTO has
no `id`/`role`/`deleted` fields a client could try to smuggle in; (3) avoiding lazy-loading
serialization failures — `Credential.user` is `FetchType.LAZY` (D-06); serializing the entity
directly outside a transaction would throw or trigger an N+1, whereas the DTO never touches that
relation; (4) never leaking internal fields — `User.passwordHash` and `Credential.encryptedPassword`
physically cannot appear in a response type that has no field for them.
**Alternatives:** Manual static factories (rejected — D-10 already named MapStruct; also what
Phase 1 shipped as a stopgap, now superseded); a single shared `CredentialResponse` for both list
and detail views (rejected — couples two API contracts that may need to diverge, e.g. a lighter
list payload at scale in S4.5).
**Consequences:** Every future feature-package DTO pair gets a matching `*Mapper` interface in
that package, next to the entity, not in `common`. `CredentialResponse.from(...)` and
`CredentialDetailResponse.from(...)` are removed; call sites use the injected mapper.

### ADR-013 — Consolidated BusinessException hierarchy + generic AccessDeniedException + envelope-consistent 401/403
**Date:** 2026-08-11 · **Status:** accepted
**Context:** S1.1-S1.6 spread exception handling across per-controller `@ExceptionHandler`
methods (each controller had its own, explicitly marked `TODO(S2.3)`) plus one basic
`GlobalExceptionHandler` for validation and a catch-all. Spring Security's own 401 path used a
plain `response.sendError(...)`, which returns a servlet-container error page, not the
`ApiResponse` envelope — a shape mismatch the React client would otherwise have to special-case
(P2.3 explicitly calls this out).
**Decision:** One `common.exception` hierarchy: `ErrorCode` (the exact fixed enum from master
§9), an abstract `BusinessException` carrying its own `ErrorCode` + `HttpStatus`, and concrete
subclasses (`UserNotFoundException`, `CredentialNotFoundException`, `DuplicateEmailException`,
`InvalidCredentialsException`, `AccessDeniedException`) moved out of their feature packages.
`AccessDeniedException` is deliberately generic (replacing S1.4's `CredentialAccessDeniedException`)
because master §9 defines exactly one `ACCESS_DENIED` code shared by every entity, whereas
"not found" stays per-entity because each has its own distinct code. `GlobalExceptionHandler`
gets one handler for the whole `BusinessException` family (`ex.getHttpStatus()`/`ex.getErrorCode()`
already carry everything needed to render it) instead of one handler per concrete exception, plus
handlers for `MethodArgumentNotValidException`, `ConstraintViolationException`,
`HttpMessageNotReadableException`, `AuthenticationException`, the framework's own
`org.springframework.security.access.AccessDeniedException` (referenced fully-qualified — it
collides by name with our own type), and a catch-all that logs a correlation UUID at ERROR and
returns it in the client message so a report can be matched back to a log line without ever
exposing a stack trace or internal class name. `SecurityConfig` gets custom
`AuthenticationEntryPoint`/`AccessDeniedHandler` beans that serialize the same `ApiResponse`
envelope by hand via the app's `ObjectMapper`, since both run at the servlet-filter level, before
`DispatcherServlet` — `@RestControllerAdvice` never sees them. `DELETE /api/vault/{id}` keeps
returning a bodyless `ResponseEntity<Void>` (204) — the sole exemption from "every controller
returns `ApiResponse<T>`", because RFC 9110 §15.3.5 forbids a body on 204; there is nothing an
envelope could wrap.
**Alternatives:** Keeping per-controller handlers (rejected — exactly the duplication P2.3 asks
to remove); giving `AccessDeniedException` a per-entity name like `CredentialAccessDeniedException`
(rejected — master §9's error-code enum has no per-entity access-denied code, so a per-entity
exception class would just be extra ceremony around the same single `ACCESS_DENIED` code);
embedding the correlation id as a new top-level `ApiResponse` field (rejected — that changes the
locked envelope shape from D-11, which needs its own ADR and mentor sign-off; folding it into
the message string needed neither).
**Consequences:** Any new business exception goes in `common.exception`, extends
`BusinessException`, and needs no new handler. Any new entity needing ownership checks reuses
`AccessDeniedException` as-is.

### ADR-014 — Password strength algorithm: penalty thresholds, whole-string dictionary match, true Shannon entropy
**Date:** 2026-08-11 · **Status:** accepted
**Context:** P3.1/M-29 fixes the base scoring formula (+1 each for length>12, upper, lower,
digit, special) but leaves the penalty thresholds, dictionary-match strategy, and "Shannon
entropy" definition to be designed and documented so the score is reproducible and explainable
(the prompt's explicit requirement). The mentor's worked example (`Welcome123` → score 3, no
sequence penalty despite containing "123") constrains the sequence-detection threshold.
**Decision:** Repeat-run penalty triggers at **3+** identical consecutive characters (matches
the prompt's own examples, `aaa`/`111`). Sequence-run penalty triggers at **4+** characters,
checked as both an ascending/descending ASCII run and a keyboard-row substring (`qwertyuiop`,
`asdfghjkl`, `zxcvbnm`, `1234567890`, or their reverses) — 4, not 3, specifically so `Welcome123`'s
3-digit `123` run does not false-positive against the mentor's own worked example, while
`abcd1234` (two 4-runs) still triggers. Dictionary check is a **whole-password, case-insensitive
exact match** against a ~250-entry hand-curated list (`classpath:/password/common-passwords.txt`)
— not a substring search — so a passphrase merely containing a common word as one segment isn't
penalized, only reproducing a known-weak password exactly. `entropyBits` is the password's own
**true Shannon entropy** (`H = -Σp(c)·log2(p(c))` over its character-frequency distribution,
scaled by length) — not the common charset-pool shortcut (`length × log2(poolSize)`) some
strength meters use, since the prompt names "Shannon entropy" specifically and the pool-size
formula measures a different thing (theoretical search-space size vs. actual information content
of the submitted characters). Full detail and worked examples in `docs/password-policy.md` §1.
**Alternatives:** 3-character sequence threshold (rejected — breaks the mentor's own worked
example); substring dictionary match (rejected — more thorough but produces false positives on
otherwise-strong passphrases that happen to contain a common word, and the prompt's worked
examples don't require it); charset-pool entropy estimate (rejected — doesn't match "Shannon
entropy" literally, and rewards character-class variety twice, once in the base score and again
in entropy, whereas true per-character entropy is an independent signal: it also penalizes
repetition, which the base score's penalty already covers via a different mechanism, giving a
reviewer two independent numbers instead of one restating the other).
**Consequences:** Any future change to the dictionary file, run thresholds, or entropy formula
must update `docs/password-policy.md` in the same change — the two are required to stay in
lockstep (enforced by review, not by code).

### ADR-015 — Password generator: guarantee-then-fill-then-shuffle with SecureRandom
**Date:** 2026-08-11 · **Status:** accepted
**Context:** P3.2/M-30 requires every generated password to contain at least one character from
every *enabled* class, using `SecureRandom` exclusively. A naive implementation — build one
combined pool from all enabled classes, then pick `length` characters uniformly from it — cannot
guarantee that; at the prompt's own minimum length (8) with 4 enabled classes, a non-trivial
fraction of naive-fill outputs would be missing at least one requested class.
**Decision:** Three-step generation, entirely on one injected `SecureRandom` instance: (1) pick
exactly one character from each enabled class's pool — this alone guarantees every enabled class
appears; (2) fill the remaining `length − enabledClassCount` slots from the **union** of all
enabled pools; (3) Fisher-Yates shuffle the full result — without this step the guaranteed
characters from step 1 would always occupy the first few positions, itself a detectable,
non-random pattern. `excludeAmbiguous` (`l`, `I`, `1`, `O`, `0`) is applied per-pool before step 1
runs, so a guaranteed character is never one of the excluded ones. "At least one character class
enabled" is enforced by a custom class-level `@AtLeastOneCharacterClass` Bean Validation
constraint on `GenerateRequest` (P2.2's validation pipeline extended, not a one-off manual check)
so a violation returns the same `400 VALIDATION_FAILED` shape as every other validation failure.
**Alternatives:** Naive single-pool random fill (rejected — cannot guarantee class coverage, the
exact bug the prompt calls out by name); rejecting and retrying naive fills until they happen to
satisfy every class (rejected — non-deterministic runtime, unnecessarily complex, and strictly
dominated by the guarantee-first approach); a manual `if` check for "at least one class" outside
Bean Validation (rejected — inconsistent with every other DTO in the project, which all validate
through the same `@Valid`/`GlobalExceptionHandler` pipeline since S2.2).
**Consequences:** `java.util.Random` must never appear anywhere in this codebase — verified by
grep each session touching this area (see `docs/progress.md` S3.2 log entry for this session's
result). Any new character class added later (e.g. a Unicode/extended set) must go through the
same guarantee-then-fill-then-shuffle shape, not a shortcut.

### ADR-016 — Dedicated `password_changed_at` column; reuse detection via decrypt-hash-discard; health score weights
**Date:** 2026-08-11 · **Status:** accepted
**Context:** P3.3 needs to know how long ago a credential's *password* last changed, to flag
stale (90+ day) passwords in `GET /api/vault/health`. `credentials.updated_at` already tracks
"last modified," but it changes on **any** field edit (renaming a credential, changing its
category), which would silently reset password-age tracking on an unrelated edit — a false
freshness signal. Reuse detection needs to compare every credential's plaintext password against
every other, without ever persisting, logging, or returning plaintext.
**Decision:** New column `password_changed_at` (`V2__add_password_changed_at.sql`, backfilled to
`now()` for existing rows — their true original change date isn't recoverable, and this is
disclosed as a limitation in `docs/db-design.md`), set at credential creation and updated **only**
inside the `if (request.password() != null) { ... }` branch of `CredentialServiceImpl.update(...)`
that already isolates "the password actually, verifiably changed" (S1.4's decrypt-and-compare
logic). Reuse detection decrypts each credential's password with the existing
`AesEncryptionService`, hashes it with SHA-256, and groups by hash — plaintext and hashes exist
only as method-local variables for the duration of `getHealth(...)` and are never logged, cached,
or included in `VaultHealthResponse` (aggregate counts only). Health score formula weights
strength at 60%, uniqueness (non-reuse) at 25%, freshness (non-staleness) at 15% of the 0-100
total — strength matters most because it's the primary determinant of whether any individual
credential can be brute-forced; reuse is the second-largest real-world risk (one leaked site
compromises every reused credential); staleness alone is the weakest signal, since an old but
strong, unique password is still fine. Full formula in `docs/password-policy.md` §3.
**Alternatives:** Reusing `updated_at` for staleness (rejected — false-resets on unrelated edits,
explained above); storing/caching password hashes on the `Credential` row for faster reuse
checks across requests (rejected — persisting even a hash of the vault secret outside the
existing `encrypted_password` column widens the exposure surface for no real performance benefit
at this project's scale; recomputing per-request keeps the "decrypt, use, discard" property
airtight); equal-weighted health score thirds (rejected — doesn't reflect that a weak password is
strictly more exploitable than a stale-but-strong one, so an equal split would under-penalize the
worse condition).
**Consequences:** Any future write path that changes `Credential.encryptedPassword` must also set
`passwordChangedAt` in the same transaction, or the health score silently understates staleness
for that row. `GET /api/vault/health` scales linearly with the user's credential count (one
decrypt per credential) — acceptable at this project's scale; revisit if profiling ever shows
otherwise.

### ADR-017 — Synchronous audit writes (not AOP), no FK from AuditLog, test-only rollback-proof flag
**Date:** 2026-08-11 · **Status:** accepted
**Context:** P4.1/M-31,M-32 requires that an audit-write failure roll back the business operation
it was recording. An AOP aspect (`@Around`/`@AfterReturning` on service methods) would be the
architecturally cleaner way to add audit as a cross-cutting concern, decoupled from every
service method's own code — but the mentor's actual requirement is stronger than "record an
audit entry": it's "if the audit entry can't be written, the business write must not happen
either." An aspect running in its own advice, especially anything `@Async`, cannot guarantee
that; only code inside the *same* `@Transactional` method, in the *same* transaction, can.
**Decision:** `AuditService.record(...)` is called as a plain synchronous method call from
inside `CredentialServiceImpl`'s `create`/`update`/`delete`/`restore`/`permanentDelete` — the
same transactional boundary as the row it's describing, so a `RuntimeException` from the audit
write rolls back everything else in that method too (Spring's default rollback-on-RuntimeException
behavior does the rest; no manual rollback code needed). `AuditLog` deliberately has no JPA
relationship to `User` or `Credential` — `performedBy`/`entityId` are plain `Long` columns, not
FKs — so an audit row physically cannot be cascade-deleted when the entity it describes is
permanently removed (P4.3's explicit requirement that "audit logs MUST remain untouched"), and a
future audit-log-listing endpoint can never N+1 against `users`/`credentials` by construction
(there's no relationship to lazily walk). Proven with a test-only `app.testing.force-audit-failure`
flag (`@Value`, defaults `false`) in `AuditServiceImpl` that throws before the row is persisted —
flipped on via env var for one deliberate run, verified live: `credentials` and `audit_logs` row
counts identical before and after a forced-failure create attempt (`docs/evidence/milestone-2/s4-1-rollback-*`),
then flipped off and the same request verified to succeed and write both rows.
**Alternatives:** AOP aspect around service methods (rejected — cannot guarantee the same
transactional boundary as the business write, especially if the aspect itself needs its own
proxy/advice ordering relative to `@Transactional`, and it obscures exactly where the audit call
happens for a reviewer); a database trigger (rejected — moves business logic out of the
application layer entirely, invisible to code review, and can't easily express "field names
changed" style human-readable `details`); `@Async` audit writes (rejected outright — directly
contradicts the requirement; an async write finishes after the caller's transaction has already
committed, so it can never roll anything back).
**Consequences:** Every future credential-mutating method must remember to call
`auditService.record(...)` inside its own `@Transactional` boundary — there is no automatic
enforcement (an AOP aspect would have given that "free," at the cost of the guarantee above). A
code-review checklist item, not a compiler-enforced one.

### ADR-018 — Soft-delete convention: explicit `*DeletedFalse` queries, no-op restore instead of 409
**Date:** 2026-08-11 · **Status:** accepted
**Context:** P4.3/M-37..M-39 replaces `Credential`'s hard delete with soft delete and adds
restore/trash/permanent-delete. Two design questions the prompt explicitly asks to be decided
and documented: (1) how do "active" queries exclude deleted rows, given trash/restore/permanent
delete need to see them; (2) what happens when `restore` is called on a credential that's
already active.
**Decision:** (1) Every repository method that should only see active rows is named explicitly —
`findByUserIdAndDeletedFalse`, `findByIdAndDeletedFalse`, `search(...)` with an explicit
`AND c.deleted = false` in its JPQL — rather than a blanket `@Where`/`@SQLRestriction` on the
`Credential` entity. A class-level filter would apply invisibly everywhere, including
`findByUserIdAndDeletedTrue` (trash) and the plain `findById` used by restore/permanent-delete,
which need to see deleted rows by design; whether a given query is deleted-aware becomes
answerable by reading its method name, not by knowing about a filter declared somewhere else on
the entity. A parallel private helper split mirrors this at the service layer: `loadOwned(...)`
(excludes deleted, used by get/update/soft-delete/history) vs. `loadOwnedAny(...)` (sees
everything, used only by restore/permanent-delete). (2) `restore` on an already-active credential
is a **no-op** — 200, current unchanged state — not a 409. Master §9's `ErrorCode` enum is a
fixed, locked list with no code that fits "already active" (`SHARE_ALREADY_EXISTS` and
`SELF_SHARE_NOT_ALLOWED` are the only similarly-shaped codes, both sharing-specific); adding a
new code is a locked-decision change this session doesn't have standing to make unilaterally.
Restore is naturally idempotent, so treating a repeat call as a no-op is also just correct
behavior, not merely a workaround.
**Alternatives:** `@SQLRestriction("deleted = false")` on the entity (rejected — exactly the
invisible-scope-creep problem above: it would silently apply to trash/restore/permanent-delete
queries too unless each one used a native/bypass query, which is worse ergonomics than naming
each query explicitly); inventing a new `ErrorCode.CREDENTIAL_ALREADY_ACTIVE` for restore
(rejected — touches the locked master §9 enum without an ADR-and-mentor-sign-off cycle this
session doesn't have time for, and a no-op is arguably the more correct REST semantic for an
idempotent state-setting operation regardless).
**Consequences:** Any new query added to `CredentialRepository` must be named to make its
deleted-awareness explicit. Any future soft-deletable entity should follow the same
`loadOwned`/`loadOwnedAny` split rather than reaching for `@SQLRestriction`.

### ADR-019 — Password history: reuse window exactly 5, ciphertext reused not re-encrypted, version endpoint never exposes plaintext
**Date:** 2026-08-11 · **Status:** accepted
**Context:** P4.2/M-35,M-36 requires the last 5 passwords to be unreusable and versioned history
that "stays AES encrypted at all times," plus a history-listing endpoint the prompt explicitly
hardens: "never return the historical passwords, not even decrypted, not even to the owner."
**Decision:** `PasswordHistoryRepository.findTop5ByCredentialIdOrderByVersionDesc` caps the reuse
window at the query level (`LIMIT`/`fetch first`), not by fetching all history and truncating in
Java — the database enforces "last 5," not application code that could get the slice logic
wrong. When a password actually changes, the credential's **current** `encrypted_password` string
is copied into history as-is (no decrypt-then-re-encrypt round trip) — it's already correctly
AES-GCM-encrypted with its own IV, and re-encrypting it would be pointless extra work that also
changes nothing about its correctness. `GET /api/vault/{id}/history` is backed by a JPQL
constructor-expression query (`SELECT new ...PasswordHistoryVersionResponse(ph.version,
ph.createdAt) FROM PasswordHistory ph ...`) that never selects the `encrypted_password` column
into memory at all for that request — a stronger guarantee than "the DTO mapper happens not to
expose the field," since there is no code path in that method that ever holds the ciphertext.
Reuse-checking still happens elsewhere (`update(...)`), where the ciphertext legitimately needs
decrypting to compare against the incoming plaintext.
**Alternatives:** Fetching all history rows and taking `.limit(5)` in Java (rejected — trusts
application code to get "last 5" right on every call site, instead of the database enforcing it
structurally once); re-encrypting the current password into a fresh history ciphertext
(rejected — no correctness benefit, only extra AES operations); reusing `CredentialDetailResponse`-style
mapping for history and just omitting the password field in the DTO (rejected — omitting a field
in the *response* type still means the *service* held the plaintext-or-ciphertext in memory at
some point; the constructor-expression query avoids ever fetching it for this endpoint at all).
**Consequences:** Any new query needing password-history data must decide up front whether it
needs the ciphertext (fetch the full entity) or just metadata (add another constructor-expression
projection) — there is no single "generic" history query to reach for by default.

### ADR-020 — Bounded async executor; AuditService stays synchronous; explicit userId across the async boundary
**Date:** 2026-08-11 · **Status:** accepted
**Context:** P4.6/M-40,M-41 asks for a deliberately-sized thread pool and for specific work
(simulated email, informational activity logging, bulk password-strength recompute) to move off
the request thread — plus an explicit, documented understanding that `@Async` runs in a
different transaction and a different security context than its caller.
**Decision:** `AsyncConfig`'s `ThreadPoolTaskExecutor` ("taskExecutor" bean): corePoolSize 4,
maxPoolSize 8, queueCapacity 50, `CallerRunsPolicy` rejection (full reasoning in the class
javadoc — bounded queue + backpressure-not-drop). `AsyncTaskService` (email, activity logging)
and `CredentialServiceImpl.recomputeStrengthForUser` (resolves the `TODO(S4.6)` left since S3.3)
are `@Async("taskExecutor")`. **`AuditService.record(...)` stays synchronous, deliberately** —
see ADR-017; this is the one thing in the codebase that must NOT move onto this executor, because
an async write finishes after its caller's transaction has already committed, breaking the
rollback guarantee entirely. Every async method takes any user identity it needs as an explicit
`Long userId` parameter — `SecurityContextHolder` is empty on the worker thread, since Spring
Security's context is stored in a `ThreadLocal` tied to the request thread, not propagated to
`@Async` by default. A related, separately-found gap: `AuditServiceImpl` originally
constructor-injected `HttpServletRequest` (a request-scoped proxy) — this threw
`IllegalStateException: No thread-bound request found` the moment `DevDataSeeder` (S4.5, runs at
startup, not inside a request) called through to it. Fixed by looking up the request via
`RequestContextHolder.getRequestAttributes()` per call, returning `null` ip/userAgent when none
exists, instead of crashing — the same fix incidentally makes `AuditService` safe to call from
any future `@Async` context too, for the same underlying reason (no request thread bound).
Similarly, `AsyncConfig`'s executor is given an `MdcTaskDecorator` so log lines from async work
still carry the originating request's correlation id (found while capturing S4.7 evidence — MDC
is also `ThreadLocal` and does not propagate to a different thread pool automatically).
**Alternatives:** Letting `@Async` methods read `SecurityContextHolder` directly (rejected — it's
empty there by default; `DelegatingSecurityContextAsyncTaskExecutor` could propagate it, but
explicit parameters are simpler to reason about and impossible to get silently wrong); an
unbounded queue (rejected — turns overload into an OOM risk instead of a visible, handled
condition, see `AsyncConfig`'s javadoc).
**Consequences:** Any new `@Async` method must take its own explicit parameters for anything
request/security-context-derived: it cannot assume `SecurityContextHolder`, `RequestContextHolder`
(without a null check), or an open Hibernate session (`open-in-view: false`, ADR-established
S0.1) are available.

### ADR-021 — `GET /api/vault` dynamic filtering via JPA `Specification`, not string-built JPQL; `PagedResponse` fields finalized to the mentor's spec
**Date:** 2026-08-11 · **Status:** accepted
**Context:** P4.5/M-34 needs pagination, sorting, and up to four independently-optional filters
(category/title/username/website) freely combinable on one endpoint, always ANDed with owner and
`deleted = false`. `PagedResponse<T>` (`common/response/`) has existed unused since ADR-013
(P2.3) with placeholder field names (`page`, `size`); this is the session that actually returns
one.
**Decision:** `CredentialSpecifications` (static `Specification<Credential>` builders, one per
predicate) composed in `CredentialServiceImpl` via plain `if (filter != null) spec = spec.and(...)`
chaining — never by concatenating query strings. `sortBy` is whitelisted with a Bean Validation
`@Pattern` against the exact set of sortable `Credential` fields before it ever reaches a
repository call; an unvalidated `sortBy` would both be a 500 waiting to happen (an invalid JPA
property path throws at query-execution time, not at the controller boundary) and would leak the
entity's field names to a caller probing for them. `size` is capped at 100 (`@Max(100)`) for the
same "don't let a client hand you an unbounded page" reasoning as `password_history`'s LIMIT-5 in
ADR-019. `PagedResponse`'s fields are renamed to `currentPage`/`pageSize`/`+hasNext` to match the
mentor's literal spec wording ("content, totalElements, totalPages, currentPage, pageSize, plus
first/last/hasNext") — a safe rename since the type was genuinely unconsumed until this session
(confirmed via ADR-013's own "not consumed anywhere yet" note).
**Alternatives:** Building the query by string-concatenating WHERE clauses per active filter
(rejected outright — the prompt explicitly forbids it, and it's the textbook path to injection
bugs and unreadable branching); `@Query` with optional-parameter JPQL (`:title IS NULL OR ...`)
(rejected — four independent optional filters would need a combinatorial OR-chain per field,
harder to read and to extend than composable `Specification` predicates); leaving `sortBy`
unvalidated and catching the resulting exception generically (rejected — turns an avoidable 400
into a 500-then-caught, and still leaks the schema through the error's stack trace/message
unless carefully scrubbed).
**Consequences:** Any new filterable field on `GET /api/vault` gets one more static
`Specification` method and one more `if`-chained `.and(...)` — the composition pattern is meant
to scale that way without restructuring. Any new sortable field must be added to both the
`@Pattern` whitelist and confirmed to be a real `Credential` property name.

### ADR-022 — Production logging: what's never logged, request-scoped correlation ids, size-and-time-based rotation
**Date:** 2026-08-11 · **Status:** accepted
**Context:** P4.7/M-46,M-47 asks for SLF4J logging across every meaningful business event, a
documented list of what must never appear in a log line, a request correlation id traceable from
a client-facing error back to the exact log lines, and rotation configuration that won't let logs
grow unbounded on disk.
**Decision:** **Never logged, anywhere, at any level** — account passwords (plaintext or
hashed), vault credential passwords (plaintext or ciphertext), JWTs, the `AES_SECRET_KEY`/`JWT_SECRET`
values themselves, MFA secrets (once Phase 5 adds them), and full email addresses at WARN/ERROR
(masked via `LogMasking.maskEmail` — `a***@example.com`, reused by both the async welcome-email
log and `AuthController`'s failed-login WARN). Business events get `@Slf4j` logging at the level
matching master §9's own guidance: INFO for state changes (register, login success, credential
create/update/delete/restore/permanent-delete), DEBUG for high-frequency read/developer detail
(a single credential reveal — logging every read at INFO would be the noisiest line in the
class), WARN for recoverable/expected-but-notable conditions (every `BusinessException`, plus a
dedicated failed-login line with the masked email `GlobalExceptionHandler`'s generic handler
never sees), ERROR only for the genuinely unexpected catch-all, always with the correlation id
and full stack trace. `CorrelationIdFilter` (`Ordered.HIGHEST_PRECEDENCE`, ahead of
`JwtAuthenticationFilter`) puts one UUID per request into the MDC — honoring an incoming
`X-Correlation-Id` header if present — echoes it back as a response header, and clears it in a
`finally` so Tomcat's pooled request threads never leak one request's id into the next.
`GlobalExceptionHandler`'s catch-all now reads that same MDC value instead of minting a fresh
UUID (S2.3's original behavior) purely for the 500 case, so the id a client can report actually
matches every surrounding log line, not just one. `logback-spring.xml` uses
`SizeAndTimeBasedRollingPolicy`: rolls at 10MB or daily, whichever comes first, 7-day
`maxHistory`, 200MB `totalSizeCap` (oldest archives deleted first once hit); `logs/` is gitignored
(already was, since S0.1). Per-profile levels (`DEBUG` for `com.securevault` locally, `INFO` in
prod) live in `logback-spring.xml`'s `<springProfile>` blocks now, not duplicated in
`application-local.yml`.
**Alternatives:** A fresh UUID per error instead of the request's own MDC-carried id (rejected —
S2.3's original approach; strictly worse once a correlation-id filter exists, since it can't be
grepped alongside the request's other log lines); an unbounded log file (rejected — the default
`RollingFileAppender` behavior without a policy; would eventually fill the disk); logging full
emails everywhere and relying on log-access controls alone (rejected — defense in depth per
master §9's explicit masking instruction; access controls can fail or be misconfigured, a masked
value in the log itself cannot).
**Consequences:** Any new log statement touching a password, token, key, or full email must go
through `LogMasking` or be omitted entirely — this is a review-time check, not something the
compiler enforces. Any new `@Async` work added later automatically gets correlation-id
propagation for free via `MdcTaskDecorator`, as long as it goes through the shared "taskExecutor"
bean.

### ADR-023 — Credential sharing: single AccessEvaluator, share-403 collapse, explicit cascade cleanup
**Date:** 2026-08-11 · **Status:** accepted
**Context:** P5.1/M-42..M-45 requires a single authorisation decision point every vault
operation goes through (owner → allow; else active unexpired share + permission → allow/deny;
else 403), plus business rules for self-share, duplicate share, and what happens to shares when
a credential is soft- or permanently-deleted.
**Decision:** `AccessEvaluator`/`AccessLevel` (sharing package) takes primitive `credentialId`,
`ownerId`, `userId` — never entity types — so `sharing` has no compile dependency on `vault`'s
entities, only the reverse (`CredentialServiceImpl` calls into `sharing`). `CredentialServiceImpl`
gained a `loadWithAccess(id, userId, requireEdit)` path used only by `getByIdForUser`/`update`;
`delete`/`restore`/`permanentDelete`/history stay strictly owner-only via the existing
`loadOwned`/`loadOwnedAny`. For `PUT`/`DELETE /api/share/{shareId}`, a shareId that doesn't exist
and one that exists but belongs to someone else both collapse to the same 403 `ACCESS_DENIED` —
master §9's fixed `ErrorCode` enum has no `SHARE_NOT_FOUND`, and this avoids disclosing whether a
given shareId exists at all (same anti-enumeration spirit as login never distinguishing
unknown-email from wrong-password). `UserNotFoundException` gained a `String email` constructor
overload for resolving `sharedWithEmail` — a deliberate, accepted exception to login's
never-disclose-existence pattern, since sharing inherently requires telling the sharer whether the
target email is registered. `credential_shares.credential_id` has no `ON DELETE CASCADE` (same
reasoning as `password_history`, ADR-019) — `permanentDelete()` calls
`credentialShareRepository.deleteByCredentialId(id)` explicitly, before deleting the credential,
making "no orphaned shares survive permanent delete" an explicit code decision, not implicit
schema behaviour. Revoke is soft (`active=false`), idempotent, and a partial unique index
`(credential_id, shared_with_user_id) WHERE active` allows re-sharing after a revoke without
blocking on old inactive rows.
**Alternatives:** `AccessEvaluator` taking `Credential`/`User` entities (rejected — creates a
`sharing↔vault` circular type dependency); a new `SHARE_NOT_FOUND` error code (rejected — same
reasoning as ADR-018's restore no-op: the fixed enum is a locked decision, inventing one needs an
ADR + explicit sign-off beyond this session, and 403 already conveys "you can't do this" without
a new code); `ON DELETE CASCADE` on `credential_shares` (rejected — same reasoning as
`password_history`: explicit cleanup in code is auditable and ordered, an implicit cascade is not).
**Consequences:** Every new vault operation must decide, explicitly, whether it goes through
`loadWithAccess` (shareable) or `loadOwned`/`loadOwnedAny` (owner-only) — there is no default.
Verified live: the full master §12 matrix (owner/READ/EDIT/unrelated/revoked/expired × view/
update/delete) plus soft-delete and permanent-delete interactions with active shares, all
behaving exactly as specified.

### ADR-024 — Refresh token rotation, reuse detection, and Redis denylist fail-open policy
**Date:** 2026-08-11 · **Status:** accepted
**Context:** P5.2 requires access+refresh tokens, rotation on refresh, reuse-detection that
revokes "the whole token family," and an access-token denylist for logout — plus an explicit
fail-open/fail-closed decision if Redis is down.
**Decision:** Refresh tokens are opaque `SecureRandom` strings (`TokenHasher.generateRawToken`,
32 bytes, base64url), never JWTs — only their SHA-256 hash is stored (`refresh_tokens.token_hash`),
mirroring `password_hash`. Every token minted from one login shares a `token_family` UUID;
rotation keeps the family, reuse of an already-revoked token revokes every token in that family
via one `UPDATE ... WHERE token_family = :family AND revoked = false` — found live, this only
actually works with `@Transactional(propagation = ..., noRollbackFor = TokenInvalidException.class)`
on `RefreshTokenServiceImpl.refresh()`: Spring's default rollback-on-any-RuntimeException would
otherwise undo the family revoke the instant `TokenInvalidException` is thrown to signal the
reuse to the caller, silently defeating the entire feature (confirmed by replaying a token twice
before the fix — the second replay still succeeded). Access tokens gained a `jti` (registered JWT
ID claim, `Jwts.builder().id(...)`) and `extractExpiration()`; `TokenDenylistServiceImpl`
(`jwt:denylist:<jti>`, TTL = remaining token lifetime) is checked by `JwtAuthenticationFilter`
alongside the existing signature/expiry check. **Fail-open, explicitly**: if Redis is unreachable,
`isDenylisted()` returns false and `denylist()` no-ops after a WARN — a fail-closed design would
make a Redis outage take down every authenticated request in the app; the accepted exposure is
narrow (a logged-out token keeps working for at most its remaining ≤15-minute natural lifetime,
only if Redis happens to be down at that exact moment). Verified live by stopping the Redis
container mid-session: login and authenticated calls kept working, with the documented WARN lines
appearing exactly as designed.
**Alternatives:** Fail-closed on Redis unavailability (rejected — availability tradeoff judged
worse than the narrow, time-bounded exposure, and documented here specifically so it's a
deliberate choice, not a silent default); JWT refresh tokens instead of opaque+hash (rejected —
opaque tokens are the only way to guarantee server-side revocability without also tracking every
issued JWT's state, which is what a denylist already does for access tokens — no reason to
duplicate the pattern for refresh tokens too).
**Consequences:** Any future `@Transactional` method that raises a business exception *after* a
side effect that must survive the exception needs the same `noRollbackFor` treatment — this is
now a known pattern in this codebase, not just a one-off fix.

### ADR-025 — Proxy and transaction-boundary lessons: cache-vs-authorization ordering, AFTER_COMMIT listeners, Jackson generics, Swagger in prod
**Date:** 2026-08-11 · **Status:** accepted
**Context:** Three related "found live, not by inspection" bugs surfaced across S5.3/S5.6/S5.7,
all rooted in the same class of issue — Spring proxies and transaction lifecycle doing something
non-obvious at the exact boundary a naive implementation assumed was simple. Grouped into one ADR
since they're the same *lesson* even though they hit different features. A fourth, unrelated
session-close decision (Swagger exposure in prod) is appended here rather than given its own ADR.
**Decision (1 — cache-bypasses-authorization):** `@Cacheable` intercepts a method call *before*
the method body runs; if the ADMIN-role check lives inside a cached method, a cache hit for a
non-cached-key... more precisely, if the check and the cached computation are the *same* method,
a warm cache entry serves the response without the check ever re-running. Found live testing
`GET /api/admin/stats`: an admin's call warmed a constant-keyed cache entry, and a genuinely
different non-admin user's very next call (same 2-minute TTL window) got 200 with real stats
instead of 403. **Fixed** by splitting into `AdminController.stats()` (never cached, runs the
`@PreAuthorize`/role check every single call) delegating to `AdminStatsServiceImpl.computeStats()`
(cached, contains no per-caller authorization logic at all). Re-verified live: a fresh non-admin
user still got 403 immediately after an admin had already warmed the cache.
**Decision (2 — AFTER_COMMIT listeners need REQUIRES_NEW):** `@TransactionalEventListener(phase =
AFTER_COMMIT)` runs synchronously in the same thread, but Spring's commit sequence calls
`triggerAfterCommit()` *before* `cleanupAfterCompletion()` unbinds the just-finished transaction's
resources — so a plain `@Transactional` (default `REQUIRED`) call made from inside the listener
can silently "participate" in that already-committed, about-to-be-torn-down transaction instead of
starting fresh. Found live: `NotificationServiceImpl.create()` returned normally with a `null` id,
no SQL `INSERT` ever reached Postgres, and no exception anywhere — the entity was built and
"saved" into a persistence context that was never really live for writing. **Fixed** with
`@Transactional(propagation = Propagation.REQUIRES_NEW)` on `create()`, forcing a genuinely new
transaction regardless of the thread's residual state. Re-verified live across all five
notification triggers (new-device, security alert, credential-shared, share-revoked,
password-expiry) — every one now persists correctly and dispatches a real email through MailHog.
**Decision (3 — Jackson default typing and Java records):** `GenericJackson2JsonRedisSerializer`
needs `ObjectMapper.DefaultTyping.EVERYTHING`, not `NON_FINAL` — Java records are implicitly
`final`, so `NON_FINAL` typing omits the `@class` type id for every record-typed DTO nested inside
a generically-typed field (e.g. `PagedResponse<T>`'s `content: List<T>`), producing an
`InvalidTypeIdException` on the very first cache *read* even though the *write* succeeded silently
(no type id needed at write time, only at read time when Jackson must decide which concrete class
to instantiate for an erased `Object`). Found live on the second call to a freshly-cached
`GET /api/vault` (first call: miss, wrote fine; second call: hit, 500). **Fixed** by switching to
`EVERYTHING`, and using a dedicated `ObjectMapper` for the cache serializer (not the shared REST
one) so `@class` metadata never leaks into an actual JSON HTTP response.
**Decision (4 — Swagger disabled in prod):** `springdoc.swagger-ui.enabled` /
`springdoc.api-docs.enabled` are `false` under the `prod` profile (`application-prod.yml`) —
enumerating every route and schema is free reconnaissance for an internet-facing credential vault
with no offsetting benefit once the mentor demo (local/staging) is done.
**Alternatives:** For (1), leaving the check inside the cached method and accepting the staleness
risk (rejected outright — this is an authorization bypass, not a UX nit, no tradeoff is
acceptable). For (2), `REQUIRED` with a manual `TransactionTemplate`/explicit flush (rejected —
`REQUIRES_NEW` is the standard, documented fix for exactly this Spring gotcha and needs no extra
scaffolding). For (3), `NON_FINAL` with a custom mixin forcing type info onto specific DTOs
(rejected — more moving parts than one enum value change, for no benefit at this project's scale).
**Consequences:** Any future `@Cacheable` method must be checked for whether it also gates access
— if so, the check moves to an uncached caller. Any future `@TransactionalEventListener(AFTER_COMMIT)`
handler that writes data needs `REQUIRES_NEW` on the write, not `REQUIRED`. Any new Redis-cached
DTO that's a record nested inside a generic container is already covered by the `EVERYTHING`
typing — no per-DTO action needed.

### ADR-026 — MFA: TOTP library, AES-encrypted secret, retry-safe challenge tokens, Redis replay guard
**Date:** 2026-08-11 · **Status:** accepted
**Context:** P5.4 requires TOTP via a maintained library, a short-lived MFA challenge exchanged
separately from the main login call, backup codes, ±1 time-step clock-skew tolerance, and replay
protection against reusing an already-accepted code within its own validity window.
**Decision:** `dev.samstevens.totp` (+ `com.google.zxing` for the QR PNG) — a maintained,
purpose-built RFC 6238 library rather than hand-rolled HMAC-based OTP. The secret is AES-256-GCM
encrypted (`User.mfaSecret`, same format/service as vault passwords, D-05) — never BCrypt, which
would make verifying a live code against it structurally impossible. `DefaultCodeVerifier` is
configured with `setAllowedTimePeriodDiscrepancy(1)` explicitly rather than relying on the
library's own default. The login-time MFA challenge token (`MfaChallengeServiceImpl`, Redis,
`mfa:challenge:<token>` → userId, 2-minute TTL) is **peeked, not consumed, on every attempt** —
found live: an initial `consumeChallenge()`-on-every-call design deleted the token the moment a
*wrong* code was tried, so one mistyped digit force-restarted the entire login from password entry
instead of allowing a retry within the 2-minute window. **Fixed** by splitting into
`peekChallenge()` (read-only, used to resolve the userId before verifying the code) and
`invalidateChallenge()` (called only after a code actually verifies), re-verified live: a wrong
code followed by a correct code against the *same* challenge token now succeeds. Replay protection
for TOTP codes (not backup codes, which are separately single-use via BCrypt+`used` flag) is a
Redis key `mfa:used:<userId>:<code>`, TTL 90s (3 time-steps, covering the full ±1-discrepancy
validity window) — set only after a code verifies, checked before every verification attempt.
**Alternatives:** `googleauth` (rejected — `dev.samstevens.totp` bundles QR generation via zxing
directly, one less integration point); consuming the challenge token unconditionally (rejected —
the bug above, found live, is exactly why); no replay guard, relying on `isValidCode`'s own
time-window check alone (rejected — that check accepts the *same* code repeatedly within its
window by design, which is precisely what M-... requires guarding against separately).
**Consequences:** Any future short-lived, retry-tolerant challenge/token flow in this codebase
should follow the peek/invalidate split, not a single consume-on-lookup method.

### ADR-027 — Brute-force lockout derived from login_attempts (no locked_at column) + Redis anomaly-rate counters
**Date:** 2026-08-11 · **Status:** accepted
**Context:** P5.5 requires locking an account after 5 consecutive failures within 15 minutes,
automatic unlock after 30 minutes, a generic (non-disclosing) 401 for locked accounts, and four
independently-testable anomaly rules (new device, elevated failures, excessive vault access, mass
permanent delete), each raising a persisted `SecurityAlert`.
**Decision:** `users.account_locked`/`failed_login_attempts` (present in the schema unmapped
since S0.1) are now mapped, but there is **no `locked_at` column** — master §10's schema doesn't
have one, and the 30-minute auto-unlock instead derives from `login_attempts`'s most recent
failure timestamp for that email (`CustomUserDetailsService`, one indexed `MAX(attempted_at)`
query, run on every login attempt before the password check). `UserPrincipal.isAccountNonLocked()`
now reflects `user.accountLocked` as of construction time — since the auto-unlock check runs
*before* `UserPrincipal` is built, Spring's `DaoAuthenticationProvider` throws `LockedException`
pre-authentication for a still-locked account, and `AuthController` catches it and throws the
exact same `InvalidCredentialsException` a wrong password would — a locked account is
indistinguishable from a wrong password from the outside, same anti-enumeration reasoning as
unknown-email. Anomaly rules 3 and 4 (excessive vault access, mass permanent delete) use Redis
rolling counters (`VaultAnomalyDetectorImpl`, `INCR`+`EXPIRE`, fixed window/threshold,
`SETNX`-guarded so the alert itself fires once per window, not once per request past the
threshold) rather than a Postgres-backed rate table — the same category of cheap, ephemeral,
high-frequency state as the MFA replay guard and JWT denylist, not a durable record in its own
right (the `SecurityAlert` it raises *is* the durable record).
**Alternatives:** A new `locked_at` column (rejected — the exact information already exists,
derivably, in `login_attempts`; adding a column to duplicate it is schema bloat for no new
capability); a Postgres table for the anomaly rate counters (rejected — Redis `INCR`+`EXPIRE` is
the textbook fit for "count events in a rolling window," and Postgres would need its own
window-cleanup job Redis's TTL gives for free).
**Consequences:** Any future per-user rate-limited counter in this codebase should default to the
same Redis `INCR`+`EXPIRE`+`SETNX`-guard pattern rather than inventing a new mechanism.

### ADR-028 — Notification event model: SecurityAlertRaisedEvent reuse, password-expiry scheduled sweep
**Date:** 2026-08-11 · **Status:** accepted
**Context:** P5.6 requires an in-app `Notification` + async email for five triggers (new-device
login, security alert, credential shared, share revoked, password expiry >90 days), fired only
after the triggering transaction actually commits.
**Decision:** `NotificationEventListener` is one `@Component` with four
`@TransactionalEventListener(phase = AFTER_COMMIT)` handlers — `onSecurityAlert` covers **two**
of the five triggers (new-device and security-alert) by branching on `AlertType.NEW_DEVICE`,
since S5.5 already raises a `SecurityAlert`/publishes `SecurityAlertRaisedEvent` for both; no
separate "new device" event was introduced. `CredentialSharedEvent`/`ShareRevokedEvent`
(published from `CredentialShareServiceImpl`) and `PasswordExpiryWarningEvent` (published from a
new `PasswordExpiryScheduler`, `@Scheduled(cron = "${app.notification.password-expiry-cron:0 0 6
* * *}")`, default once daily, overridable via property for local verification without touching
the committed schedule) cover the remaining three. The password-expiry sweep is rate-limited to
once per user per 7 days via a Redis `SETNX` guard (same pattern as ADR-027's anomaly counters) —
without it, a daily sweep would re-notify about the same stale credentials every single day.
Emails go through `EmailServiceImpl`, `@Async("taskExecutor")`, `JavaMailSender` against MailHog
locally (`spring.mail.host=localhost:1025`, no auth/TLS) — failures are caught and logged at WARN,
never propagated, since by the time an email is being sent the triggering business transaction has
already committed and there is nothing left to roll back.
**Alternatives:** A separate `NewDeviceLoginEvent` distinct from `SecurityAlertRaisedEvent`
(rejected — would duplicate exactly what `SecurityAlertRaisedEvent` already carries for that
specific alert type, for no behavioural difference); firing password-expiry notifications
on-demand per credential rather than via a scheduled sweep (rejected — "credentials older than 90
days" is inherently a state-based condition to detect proactively, not something a specific user
action triggers).
**Consequences:** Any future notification trigger tied to an existing `SecurityAlert` type should
branch inside `onSecurityAlert` rather than adding a new event; anything not already modeled as a
security alert gets its own event, published from wherever the business transaction actually
commits.

### ADR-029 — Dashboard aggregation: database-level GROUP BY over in-memory grouping, 2-minute cache TTL
**Date:** 2026-08-11 · **Status:** accepted
**Context:** P5.7 requires read-only aggregate dashboard endpoints computed via database
aggregation, not by loading entities into memory, cached briefly with a documented staleness
window.
**Decision:** `byCategory`/favorites/total counts use grouped `COUNT(...)`/`GROUP BY` JPQL
queries (`CredentialRepository.countByCategoryForUser`, `countByUserIdAndDeletedFalse*`) — no
full-vault load followed by an in-memory `Collectors.groupingBy`. The one deliberate exception is
`passwordHealth()`'s "top 5 items to fix" ranking, which sorts a single user's own already-bounded
active-credential list — the exact same list `CredentialServiceImpl.getHealth()` already loads for
that same user (S3.3 precedent); this is a bounded, per-user operation, not the whole-table scan
the "aggregate in the database" rule is aimed at preventing. `summary`/`passwordHealth` are cached
2 minutes (RedisCacheConfig's `dashboard` region, shared with `AdminStatsServiceImpl`); staleness
is **time-based only, no active eviction** on vault/share mutations — unlike `vaultList`'s
immediate eviction (a real security/correctness concern there), a dashboard being up to 2 minutes
stale is an accepted, documented UX tradeoff. `recentActivity` is explicitly **not** cached — a
2-minute-stale count reads as normal, but a just-performed action missing from "recent activity"
reads as broken to the person who just did it.
**Alternatives:** Evicting the `dashboard` cache on every vault/share mutation, mirroring
`vaultList` (rejected — would require touching every mutation across `vault`, `sharing`, and
`monitoring`, for a widget where a few minutes of staleness is the explicitly accepted design per
P5.7's own wording); caching `recentActivity` too (rejected — see above).
**Consequences:** Any new dashboard aggregate should default to a grouped database query and the
2-minute `dashboard` cache region unless it's an activity-feed-shaped endpoint, which stays
uncached by the same reasoning as `recentActivity`.

### ADR-030 — Admin module: manual role checks retired in favour of @PreAuthorize, method security enabled
**Date:** 2026-08-11 · **Status:** accepted
**Context:** P5.8 requires `GET /api/admin/users` (paginated, searchable), `PUT
/api/admin/users/{id}/status`, `GET /api/admin/audit-logs` (filterable by user/action/date range),
all `@PreAuthorize("hasRole('ADMIN')")`, with method security enabled if not already.
**Decision:** `@EnableMethodSecurity` added to `SecurityConfig` (Spring Security 6's replacement
for the older `@EnableGlobalMethodSecurity`). All four `AdminController` routes
(`stats`/`users`/`users/{id}/status`/`audit-logs`) use `@PreAuthorize("hasRole('ADMIN')")`,
replacing `AdminController`'s earlier manual `requireAdmin()` check from S5.7 — now that method
security exists, there's no reason for two different admin-gating mechanisms in the same
controller. `MonitoringController`'s manual `?all=true` role check (S5.5) is deliberately **left
as-is**: it gates one query-parameter-driven branch inside an endpoint every authenticated user can
call, not a whole-endpoint 403, so `@PreAuthorize` doesn't fit the same way. User search
(`UserSpecifications.emailOrNameContains`) and audit-log filtering
(`AuditLogSpecifications.performedBy`/`action`/`timestampAfter`/`timestampBefore`) both extend
`JpaSpecificationExecutor`, composing optional filters exactly like `CredentialSpecifications`
(D-12/ADR-021) — no string-concatenated JPQL. `springdoc-openapi-starter-webmvc-ui` (2.8.5) is
configured with a `bearerAuth` HTTP/bearer security scheme so Swagger UI's Authorize button works
with a raw access token, and every controller carries an explicit `@Tag` so Swagger UI groups
routes by feature module rather than by raw controller class name.
**Alternatives:** Converting `MonitoringController`'s `?all=true` check to `@PreAuthorize` too
(rejected — it isn't a whole-endpoint gate, and forcing it into that shape would need splitting
one endpoint into two just to fit the annotation, for no real benefit); leaving `AdminController`'s
manual check in place alongside the three new `@PreAuthorize` routes (rejected — inconsistent
within the same controller for no reason once method security exists).
**Consequences:** Any new admin-only, whole-endpoint route should use `@PreAuthorize`, not a
manual role check — the manual pattern is now reserved specifically for "one endpoint, multiple
authorization-scoped branches" cases like `MonitoringController`'s.

---

### ADR-031 — Frontend stack pins: React 18 over Vite's React 19 default, Tailwind v4 CSS-first config
**Date:** 2026-08-11 · **Status:** accepted
**Context:** D-14 locks "React 18 + Vite + JavaScript." `npm create vite@latest` (current version)
scaffolds React 19 and pulls in `@types/react`/`@types/react-dom` 19 as transitive peers even for
the plain-JS template. Tailwind's current major (v4) also changes setup shape from what most
existing tutorials/D-14-era expectations assume: no `tailwind.config.js`/`content` globs by
default, no `@tailwind base/components/utilities` directives — configuration is CSS-first via
`@import "tailwindcss"` plus an `@theme` block, and Vite integration is a dedicated
`@tailwindcss/vite` plugin rather than a PostCSS config file.
**Decision:** Pinned `react`/`react-dom` to `^18.3.1` immediately after scaffolding (verified via
`npm ls` that every dependency — Redux Toolkit, React Router, react-hot-toast, lucide-react —
deduped onto the same 18.3.1, no dual-version tree) and removed the stray `@types/*` packages
(unused in a JS project). Kept Tailwind at its current major (v4) rather than downgrading to v3
for tutorial-familiarity — v4's CSS-first `@theme` block is actually a better fit for "a small
design token set: one accent colour, a neutral scale, consistent radius and spacing" (P6.1) than
hand-writing a `tailwind.config.js` theme object, and downgrading a fresh scaffold to an older
major for no functional reason isn't a good trade. Design tokens live in `src/index.css`'s
`@theme` block (`--color-accent-*`, `--color-neutral-*`, `--radius-sv`).
**Alternatives:** Downgrading Tailwind to v3 to match older tutorial conventions (rejected — no
functional benefit, and v4's CSS-first config is a genuine improvement for this exact use case);
leaving React at v19 and treating D-14 as stale (rejected without asking first — D-14 is an
explicit locked decision naming React 18, and the PDF's evaluation criteria follow the spec).
**Consequences:** Any future `npm install`/`npm update` must not float `react`/`react-dom` past
18.x without a fresh ADR. Tailwind class names and the `@theme` token setup will look unfamiliar
to anyone expecting a v3-era `tailwind.config.js` — noted here so that's not mistaken for a
missing file.

---

### ADR-032 — Redux Toolkit thunks (not RTK Query) for data fetching; one axios client owns all HTTP
**Date:** 2026-08-11 · **Status:** accepted
**Context:** P6.1 requires "RTK Query or thunks for vault data — pick one, use it everywhere,
record the ADR." The same session also requires a hand-built axios instance with a specific
interceptor contract: attach the bearer token, detect a 401 from an authenticated request, queue
concurrent 401s behind a single in-flight `/api/auth/refresh` call, retry them with the rotated
token, and clear auth + redirect on refresh failure — all while never retrying the refresh
endpoint itself (loop guard).
**Decision:** `createAsyncThunk` (Redux Toolkit thunks) throughout — `authSlice`
(register/login/MFA challenge/logout) and `vaultSlice` (list/create/update/delete/restore, each
mutation re-dispatching `fetchVaultList` with the current query rather than hand-patching local
state, mirroring the backend's own "evict the whole cache region, don't try to patch it" approach
from P5.3). Every thunk calls `apiRequest()` (`src/api/client.js`), which wraps the one axios
instance and unwraps `ApiResponse.data` centrally. Sharing, dashboard, and admin features call the
same `api/*.js` modules directly from component state (`useState`/`useEffect`) rather than through
Redux, since nothing about their data needs to be shared across routes or survive a navigation —
promoting everything to Redux "for consistency" would just be unused global state.
**Alternatives:** RTK Query (rejected — its `fetchBaseQuery`/`baseQuery` model wants to own the
HTTP layer itself; bolting the required refresh-queue-and-retry interceptor logic underneath it
would mean either fighting RTK Query's own retry/cache-invalidation model or duplicating the
interceptor a second time outside it — plain axios interceptors are the natural home for exactly
the queueing behavior P6.1 specifies, and thunks compose with that client for free). Redux for
every feature, not just auth/vault (rejected as over-centralization — see decision above).
**Consequences:** Any new feature module needing to *share* server state across multiple routes
(not just fetch-and-render once) should get its own slice + thunks following the `vaultSlice`
pattern; anything route-local should stay component state calling `api/*.js` directly.

---

### ADR-033 — Dashboard charts: plain markup bars over a charting library; SVG only for the score dial
**Date:** 2026-08-11 · **Status:** accepted
**Context:** P6.6 asks for "a simple category distribution chart and a strength distribution
chart (use a light chart library or plain SVG — record the choice in an ADR)."
**Decision:** Both distribution charts (`CategoryChart`, `StrengthChart`) render as horizontal
bar lists — a labeled row per category/strength-band, a proportionally-widthed `div` bar, a count
— using plain Tailwind-styled `<div>`s, not SVG and not a charting library. The one place that
genuinely benefits from SVG is the circular health-score dial (`ScoreDial`, shared by the vault
page's health widget and the dashboard's password-health card), which needs a true arc and has
exactly one data point, not a multi-category comparison. No charting library (`recharts`,
`chart.js`, etc.) was added.
**Alternatives:** A charting library (rejected — zero new dependencies was already the norm for
this frontend per P6.1's "nothing else without an ADR" dependency policy, and 5-7 category rows
don't need a general-purpose charting engine); hand-rolled SVG donut/pie arcs for the category
chart (rejected — arc math via `stroke-dasharray`/path trigonometry is easy to get subtly wrong,
and a sorted horizontal bar list is at least as readable for 5-7 categories while being far less
code to get right).
**Consequences:** If a future session needs a genuinely multi-dimensional chart (time series,
scatter, etc.), that's the point to actually add a charting library — the bar-list approach here
doesn't generalize past "distribution across a handful of named categories."

---

### ADR-034 — Frontend gaps discovered against a backend surface that doesn't (yet) support them
**Date:** 2026-08-11 · **Status:** accepted, tracked
**Context:** Several UI capabilities named across the P6.x prompts assume a backend write path or
field that master §11's "complete target surface" lists but that no Phase 1-5 session actually
implemented. Building the UI first and discovering the gap live (rather than assuming the backend
supports something because a prompt asked for it) is consistent with this project's
verify-against-the-running-app methodology.
**Decision:** Each gap was handled by omitting the non-functional affordance rather than faking
it against data the backend can't actually persist or act on:
1. **Forgot/reset password** (P6.2) — `POST /api/auth/forgot-password`/`reset-password` appear in
   master §11's target surface but were never assigned a mentor task or a session in the 53-session
   backlog (§5/§16). No screens built; no dead links to a nonexistent endpoint.
2. **Favorite toggle** (P6.3) — `CredentialCreateRequest`/`CredentialUpdateRequest` have no
   `favorite` field, and `CredentialMapper` explicitly carries `@Mapping(target = "favorite",
   ignore = true)` on both create and update — a deliberate prior exclusion, not an oversight, so
   this session did not reverse it unilaterally. The vault list shows the (currently always-false)
   favorite star as a **read-only** indicator, not a fake-working toggle that would return 200
   while silently changing nothing.
3. **Trash "deletion date"** (P6.5) — `CredentialSummaryResponse` (reused for the trash list) has
   no `deletedAt` field, only `updatedAt`. Since a soft delete sets both in the same write, the
   trash view labels the `updatedAt` column "Deleted" — accurate for a trashed row, not fabricated.
4. **Alert dismiss action** (P6.6) — `SecurityAlertResponse` carries an internal `resolved` flag
   that no endpoint ever sets from the outside. The dashboard/admin alert panels are read-only.
5. **Admin-wide "active devices"** (P6.7) — `GET /api/monitoring/devices` only ever returns the
   caller's own devices; unlike login-attempts/alerts, it never grew a `?all=true` admin scope.
   The admin security-monitoring tab covers login attempts and alerts (both admin-scoped) and
   omits a platform-wide device list rather than showing one user's devices mislabeled as global.
6. **Admin users table "last login"** (P6.7) — `AdminUserResponse` has no such field; the column
   was omitted rather than showing a placeholder.
**Alternatives considered and rejected for every item above:** adding the missing backend field/
endpoint during this frontend-only phase (rejected — scope creep into backend behavior without
the "write an ADR and ask first" step CLAUDE.md requires for anything touching a locked layer,
and several of these — favorite in particular — look like deliberate prior exclusions, not bugs);
faking the UI against data the backend can't support (rejected outright — a button that returns
200 while changing nothing is worse than no button, and this project's explicit rule is "no
client-side aggregation or fabrication — the server's answer is the one that counts").
**Consequences:** If a future phase needs any of these six capabilities for real, each needs its
own backend session (new DTO fields / endpoints / migration where relevant) before the frontend
affordance can honestly become interactive. Listed here as a single tracked backlog rather than
six one-line TODOs scattered across component comments.

---

### ADR-035 — Admin-imposed account lock is silently undone by the existing auto-unlock heuristic (found live, not fixed)
**Date:** 2026-08-11 · **Status:** accepted, tracked (backend bug, out of scope for Phase 6)
**Context:** While live-verifying S6.7's admin lock/unlock UI against the real backend: locking a
user via `PUT /api/admin/users/{id}/status {"locked":true}` sets `account_locked=true` correctly
(confirmed via direct `psql`), but the **very next login attempt by that user succeeds**, and
`account_locked` flips back to `false` — even though no unlock call was ever made.
**Root cause:** `CustomUserDetailsService` (P5.5) auto-clears `accountLocked` on login whenever the
user's most recent *failed* login attempt is more than 30 minutes old, treating "no recent
failure" as "safe to auto-unlock." That heuristic was written for the brute-force-lockout case
(5 failures -> auto-unlock after 30 min), which is exactly right there. But it can't distinguish
"this user was auto-locked by the brute-force detector" from "an admin just locked this account
on purpose" — both are the same `account_locked` column. A user with a clean failure history
(`failed_login_attempts = 0`) has, by definition, no failure recent or otherwise, so the very
first login attempt after an admin lock passes the "most recent failure is stale/absent" check
and silently clears the lock.
**Verified live:** locked `seed.user` via the admin API -> confirmed `account_locked = t` via
direct SQL with zero login attempts in between -> seed user's next `/api/auth/login` call
returned 200 with real tokens -> SQL immediately after showed `account_locked = f`.
**Decision:** Not fixed in this phase. This is backend authorization logic (`CustomUserDetailsService`,
P5.5), and Phase 6 is scoped to frontend work — the project's own rule is to flag a conflict and
ask before touching a different phase's already-shipped, working-as-far-as-P5.5-tested logic,
rather than silently patch it mid-frontend-phase. The frontend itself behaves correctly: it calls
the real endpoint and displays whatever state the backend returns, so there's no frontend bug to
fix — the lock genuinely does apply and genuinely does get undone by the backend's own logic.
**Alternatives (for the eventual fix, not applied here):** a separate `admin_locked` boolean
distinct from the brute-force `account_locked`/`failed_login_attempts` pair; or having the
auto-unlock check require *some* failure history to exist before auto-clearing, rather than
treating "no failures on record" as automatically eligible.
**Consequences:** Until fixed, the admin "lock user" feature is reliable only for accounts that
already have failed login attempts on record (i.e., it durably reinforces an existing brute-force
lock) — locking an otherwise-clean account is cosmetic and self-reverses on that user's next
successful login. Flagged to the developer directly, not just buried in this file.

---

### ADR-036 — Testcontainers: singleton-container pattern, not `@Container` (found live, fixed)
**Date:** 2026-08-12 · **Status:** accepted
**Context:** P7.2's four integration test classes (`VaultJourneyIntegrationTest`,
`VaultPaginationIntegrationTest`, `SharingJourneyIntegrationTest`, `AuditRollbackIntegrationTest`)
all extend `AbstractIntegrationTest`, which declares one shared `static PostgreSQLContainer` and
one shared `static GenericContainer` (Redis) so the whole suite pays container-startup cost once,
not once per class. The first draft annotated both fields `@Container` (plus `@Testcontainers` on
the class). Running any single class in isolation passed; running the full `mvn test` suite made
every class *after* the first fail its very first `register`/`login` call with a **500**.
**Root cause (found live, not theoretical):** `@Container` ties a container's start/stop to *its
owning test class's* JUnit lifecycle — started in that class's `@BeforeAll`, stopped in its
`@AfterAll`. A `static` field is genuinely one shared instance across every subclass, but the
Testcontainers JUnit5 extension's start/stop bookkeeping is per-class, not per-field: the first
class to finish its tests stopped the (shared) container out from under every class that ran
after it, and Spring's `@ServiceConnection`-injected `DataSource`/`RedisConnectionFactory` still
pointed at the now-dead container's cached host/port.
**Decision:** Removed `@Container` from both fields; start both containers exactly once in a
`static` initializer block instead, and never call `.stop()` on them — Testcontainers' own Ryuk
reaper container cleans them up when the whole JVM/test run exits. `@ServiceConnection` is kept
(it only wires connection details into Spring's context; it never controlled lifecycle). This is
Testcontainers' own documented "singleton containers" pattern — the fix wasn't a workaround, it
was switching to the pattern the `@Container` shortcut is explicitly *not* meant for when a
container is shared across multiple test classes.
**Verified:** full `mvn clean verify` — 90/90 tests green (was 84/90 with 6 failures before the
fix, then a separate real test-isolation bug in `VaultPaginationIntegrationTest` surfaced and was
fixed too — see docs/evidence/security-matrix.md, Finding 3).
**Consequences:** Any future integration test class must extend `AbstractIntegrationTest` (not
declare its own containers) to stay inside this shared-singleton pattern; a class that needs
different container configuration would need its own, separately-lifecycled containers, not a
`@Container` override on the shared fields.

---

### ADR-037 — Frontend tests: MSW at the network boundary, `.env.test` over per-handler wildcards
**Date:** 2026-08-12 · **Status:** accepted
**Context:** P7.4 requires mocking "the network at the boundary (MSW or an axios mock), not by
stubbing components" — the point being that `api/client.js`'s interceptor logic, the
`ApiResponse`-envelope unwrap, and error normalization (`api/client.js`'s `apiRequest`) all run
for real in a test, not a bypassed mock of `vaultApi.list()` etc. The first draft registered MSW
handlers with relative paths (`http.post('/api/auth/login', ...)`) and every one of them silently
failed to match, live: `[MSW] Error: intercepted a request without a matching request handler` —
the app's real `.env.local` sets `VITE_API_BASE_URL=http://localhost:8080`, which Vite also loads
under Vitest, so axios builds an **absolute** URL and MSW's relative-path handler pattern (which
resolves against jsdom's default origin, `http://localhost:3000`) never matches it.
**Decision:** Added `frontend/.env.test` (committed, no secrets — just
`VITE_API_BASE_URL=`, empty) rather than rewriting every handler to a wildcard `*/api/...`
pattern. Vite loads `.env.[mode]` files with higher priority than `.env.local` for that mode, and
Vitest's default mode is `test`, so this one file makes every test's axios calls relative
(`/api/vault`, not `http://localhost:8080/api/vault`) without touching any test file.
**Alternatives considered:** per-handler wildcard patterns (`http.post('*/api/auth/login', ...)`)
— rejected as noisier (every handler in every test file needs the prefix) and easier to typo
into a silent non-match than one shared env file; mocking `api/client.js`'s `apiRequest` directly
— rejected, defeats the point of testing at the network boundary per the prompt's own instruction.
**Consequences:** Any new test file can register MSW handlers with plain relative paths and they
will just work; a developer adding a new `.env.test.local` (if ever needed for local-only test
overrides) would take priority over this file automatically, per Vite's own env precedence.

---

### ADR-038 — JaCoCo attempted and reverted this session; blocked on a sustained Maven Central rate limit
**Date:** 2026-08-12 · **Status:** reverted, not applied — retry in a future session
**Context:** P7.5 asks for "a realistic gate on the SERVICE layer specifically (aim ~80% there)
rather than a vanity number across the whole project." Not every `*ServiceImpl` in this codebase
has a dedicated unit test — S7.1's own explicit minimum list only named
`AesEncryptionService`/password strength/password generator/`UserService`/`CredentialService`/
password history/sharing. Admin, dashboard, monitoring, notification, MFA, refresh-token, and
async-task services are exercised indirectly (through the P7.2 HTTP-level integration tests and
the S7.3 live security matrix) but have no direct `*ServiceImplTest`. A blanket repo-wide 80%
gate would either fail the build outright or (worse) get quietly lowered to whatever number makes
it pass, which is exactly the "inflated vanity number" the prompt warns against — so the plan was
to scope `<includes>` to the S7.1-tested classes, measure the real number there, and only then
set a `check` threshold at (or just under) what was actually measured.
**What actually happened:** `jacoco-maven-plugin` 0.8.13 (`prepare-agent` + `report`, bound to the
`test` phase) was added to `backend/pom.xml`. It was **never successfully resolved** — every
`mvn clean verify` attempt (and a bare `mvn -o clean verify` offline check) failed, because the
plugin JAR had never been cached in this environment's `~/.m2` before this session and Maven
Central would not serve it (see Blocker below). Leaving an unresolvable plugin declared in
`pom.xml` would mean the build itself is broken for anyone who runs it — a build that has never
once been verified green with that change in place is not something this project ships, per its
own standing rule ("never push a non-compiling build," `docs/securevault_prompts.md` P9.1). The
plugin block and `jacoco.version` property were therefore **reverted** before this phase closed.
`mvn -o clean verify` was re-run immediately after reverting and confirmed green (90/90 tests,
Spotless clean, fully offline — no phantom dependency on the unresolved plugin remains).
**Blocker, verified not transient:** More than ten resolution attempts across roughly 70 minutes,
including direct `curl` probes of `repo.maven.apache.org` and the `repo1.maven.org` alias (both
with and without Maven itself, and against the bare repository index path, not just this one
artifact's `.pom`) all returned HTTP 429 (rate limited). General internet connectivity from the
same shell was confirmed working throughout (`registry.npmjs.org`, `github.com`, `google.com` all
returned 200 in the same window), so this is specifically Maven Central rate-limiting this
environment's shared egress IP, not a broader network failure — plausible given many concurrent
sandboxed sessions likely share the same NAT gateway. No numbers in
`docs/evidence/milestone-4/coverage.md` are estimated or fabricated to work around this; the
file says plainly what is and isn't known yet.
**Consequences:** JaCoCo is not in `backend/pom.xml` right now — this ADR documents an attempt
that was reverted, not a decision in effect. To actually add it in a future session: confirm
Maven Central is reachable first (`curl -sI https://repo.maven.apache.org/maven2/` returning 200
rather than 429), add the same `jacoco-maven-plugin` `prepare-agent`+`report` block back, run
`mvn clean verify` and confirm it succeeds before committing, read the real per-class
line-coverage percentages from `target/site/jacoco/index.html` for the eight classes named above,
then add a `check` execution bound to `verify` with `<includes>` scoped to exactly those eight
classes and a threshold at or just below the measured number — never above
it. `docs/progress.md`'s Open blockers list carries this forward explicitly so it isn't lost.

### ADR-039 — GitHub fork publishes README-only; full codebase stays local-only
**Date:** 2026-08-15 · **Status:** accepted, supersedes the publishing scope implied by ADR-006
**Context:** The developer's fork (`github.com/vigneshpraveen-official/SecureVault`, the sole
configured remote per ADR-006) was created via GitHub with an auto-generated `LICENSE` +
one-line `README.md` ("Initial commit") and has never received a push from local `main` — local
`main` is 11 commits ahead with unrelated history. The developer asked to (a) audit the repo for
files tied to the AI-agent tooling/workspace used to build this project — `CLAUDE.md`,
`AGENTS.md`, `GEMINI.md`, `docs/ai/CONTEXT.md`, `docs/ai/CONVENTIONS.md`,
`docs/securevault_prompts.md` (all currently git-tracked locally), plus the already-gitignored
`.claude/`, `00_initial_claude_code_prompt.md`, `All Tasks.txt` — and (b) ensure none of that, nor
any other project file, ever reaches the public GitHub remote; only a filled-in `README.md`
should be publicly visible there.
**Decision:** Local `main` keeps full history and stays git-tracked exactly as it is today
(backend, frontend, docs, evidence, AI-tooling files included) — nothing is untracked or removed
locally. The GitHub remote (`origin`) is treated as publishing only `README.md` (and the existing
`LICENSE`), updated via a separate branch built directly on `origin/main`'s own history (not by
pushing local `main`), so local development history and the AI-tooling files never leave the
machine. `.gitignore` gained a documented, non-destructive section listing the AI-tooling files —
it cannot untrack what's already tracked, but it stops any *new* AI-tooling file from being swept
into a future `git add -A` on any branch, public or local.
**Alternatives:** Untrack the AI files / full codebase from git entirely, even locally (rejected —
developer explicitly wants full local version history preserved for development, only the public
remote should be minimal); force-push local `main` over `origin/main`'s unrelated history to make
GitHub match local exactly, then rely on `.gitignore` alone to keep files out of future commits
(rejected — force-pushing discards `origin/main`'s existing history for no benefit and still
wouldn't retroactively remove anything, since nothing has been pushed yet).
**Consequences:** `origin/main` and local `main` remain permanently divergent, unrelated
histories by design — this is not a bug to fix. Any future push to `origin` must go through the
README-only branch, never `git push origin main`. Revisit alongside ADR-006 once the mentor gives
central-repo submission instructions (S9.1), since submission likely requires the full codebase
to reach a repo somewhere (even if not this fork's `main`).
