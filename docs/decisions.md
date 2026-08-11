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
