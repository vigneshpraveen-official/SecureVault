# SecureVault — Master Project Document

> **Single source of truth** for the SecureVault full-stack password vault built for the
> Infosys Springboard Java Full Stack virtual internship.
> Every AI agent (Claude Code, Gemini, ChatGPT, Antigravity) and every human session
> reads this file **first**. If code and this document disagree, this document wins —
> fix the code, or amend this document deliberately and log it in `docs/decisions.md`.

---

## 0. How to use this document

| You are… | Read |
|---|---|
| Starting a coding session | §14 Session Protocol → `docs/progress.md` → the session prompt |
| An AI agent picking up cold | §1, §5, §6, §8, §9, §14, §15 (mandatory) |
| Debugging / running the app | `docs/guide.md` (generated), §13 Environment |
| Preparing a milestone submission | §17 Evidence Checklist, §7 Repo Strategy |
| Making an architectural change | §6, then append to `docs/decisions.md` |

**Status of this file:** authoritative, versioned in git, amended only via explicit decision entries.

---

## 1. Project identity

| Field | Value |
|---|---|
| Name | SecureVault |
| Tagline | A full-stack password vault & credential management system |
| Context | Infosys Springboard virtual internship — Java Full Stack track |
| Duration | 8 weeks, 4 milestones (2 weeks each) |
| Backend | Java 21 + Spring Boot 3.3.x + PostgreSQL + Redis |
| Frontend | React 18 (Vite) + Tailwind CSS + Redux Toolkit + Axios |
| Deployment | Render (backend Docker service + frontend static site), Neon (Postgres), Upstash (Redis) |
| Central repo | `https://github.com/springboardmentor1295d-arch/SecureVault` |
| Working branch | `<your-full-name>` (e.g. `vignesh-praveen`) — **never** `main` |

**One-sentence definition:** SecureVault lets a user register, authenticate with JWT + MFA,
store credentials encrypted at rest with AES-256-GCM, generate and analyse passwords,
share credentials with scoped permissions, and monitor every access through audit logs
and a security dashboard.

---

## 2. Ground truth sources & authority order

When two sources conflict, the **higher** one wins:

1. **Mentor's live instructions** (WhatsApp / session) — highest. They are what gets graded.
2. **`All_Tasks.txt`** — the accumulated mentor task stream (transcribed in §5).
3. **Official project PDF** — the 8-week spec, module list, and stack.
4. **This document** — engineering decisions that fill the gaps in 1–3.
5. **AI agent judgement** — lowest. Never silently override 1–4.

> **Rule:** if an AI agent proposes something that contradicts §5 or §6, it must stop and
> ask, not "improve" the design on its own.

---

## 3. Two-track scope model

The PDF describes a far larger product than the mentor actually grades. Do not confuse them.

### Track A — GRADED (must ship, on schedule)
Everything in §5. This is the mentor's task stream: backend-first, Spring Boot, PostgreSQL,
JWT, AES, DTOs, password intelligence, transactions, audit, pagination, history, soft delete,
async, sharing, logging. **Track A is never delayed for Track B.**

### Track B — SPEC COMPLETENESS (for final demo, documentation, portfolio)
React frontend, MFA/TOTP, OAuth2, Redis caching, refresh tokens, WebSocket alerts,
notifications, analytics dashboards, reports/export, Docker, CI/CD, cloud deployment.
Required by the PDF for Milestones 3–4 and the final demonstration.

### Track C — EXPLICITLY OUT OF SCOPE
Do not build these unless the mentor asks: biometric auth, SMS/Twilio, Firebase push,
Kafka/RabbitMQ, Kubernetes, ELK stack, AWS S3 (replaced by local/Cloudinary-free storage),
IP-reputation threat-intel feeds, microservice decomposition. They appear on the PDF's
architecture poster but are not achievable or necessary at zero budget in 8 weeks.
**A monolith with clean module packages is the correct architecture here.** Document this
choice in `docs/decisions.md` so it reads as deliberate, not as a shortcut.

---

## 4. Current position

- **Code state:** day zero — nothing built.
- **Mentor stream state:** already past sharing, password history, soft delete, pagination, logging.
- **Implication:** Phases 1–4 are **catch-up sprints**. The mentor deliberately staged some
  steps to teach (e.g. "store the password as plain text today, add BCrypt tomorrow").
  We implement the *correct final version directly* but still produce the *evidence artifacts*
  he grades (screenshots, Postman collections, before/after SQL logs, explanation notes).
- **Working style:** variable daily time. Work is therefore cut into **atomic sessions** —
  each ends with a green build, a commit, and an updated `docs/progress.md`.

---

## 5. Canonical mentor task stream (from `All_Tasks.txt`)

This is the graded backlog, reconstructed in dependency order. Duplicates and diagram
noise removed. **Nothing here may be skipped.**

| # | Mentor task | Key requirements | Phase |
|---|---|---|---|
| M-01 | Product decomposition | List every feature + why it exists (Feature / Why table) | 0.2 |
| M-02 | Architecture reasoning | Browser → React → Spring Boot → PostgreSQL, then place JWT, encryption, Redis, audit logs, email — by reasoning, not Google | 0.2 |
| M-03 | PostgreSQL setup | Install, run service, create DB named `securevault`, no tables yet | 0.3 |
| M-04 | Schema design | Tables + columns + types + PK + FK. Minimum: User, Credential, Category, SharedCredential, AuditLog, Notification, Device | 0.3 |
| M-05 | ER diagram | Deliverable: ERD image (dbdiagram.io / draw.io / Excalidraw) | 0.3 |
| M-06 | Registration API | `POST /api/auth/register`, Controller→Service→Repository→PostgreSQL, duplicate-email handling, JPA + Hibernate, tested in Postman. *(No login/JWT/Security in the original step.)* | 1.1 |
| M-07 | BCrypt hashing | Only the hash is stored; same password → different hashes | 1.1 |
| M-08 | Credential entity + create | `POST /api/vault`, fields: id, title, username, encryptedPassword, websiteUrl, notes, user, createdAt, updatedAt. Column **must not** be named `password` | 1.3 |
| M-09 | AES integration | Request → Service → AES encrypt → Repository → DB. DB must never hold the plaintext | 1.3 |
| M-10 | Read + decrypt | `GET /api/vault/{id}` → decrypt → return original password | 1.3 |
| M-11 | Update credential | `PUT /api/vault/{id}`; re-encrypt only if the password changed | 1.4 |
| M-12 | Delete credential | `DELETE /api/vault/{id}`; must not affect other rows | 1.4 |
| M-13 | Ownership verification | Reject access to another user's credential | 1.4 |
| M-14 | **Milestone 1 checklist** | Project runs, PostgreSQL connected, schema final, register, BCrypt, login, vault CRUD, AES, validation, exception handling, Postman, clean structure | 1.6 |
| M-15 | Spring Security config | Disable default login page, stateless, `SecurityFilterChain`, permit `/api/auth/**`, protect `/api/vault/**` | 1.2 |
| M-16 | JwtService | Generate token, extract username, validate, check expiration | 1.2 |
| M-17 | JwtAuthenticationFilter | Read `Authorization` header → validate → authenticate → continue chain | 1.2 |
| M-18 | Login → JWT | Verify BCrypt → generate JWT → return it | 1.2 |
| M-19 | JWT test matrix | No JWT → 401; valid JWT → 200; expired JWT → 401 | 1.2 |
| M-20 | Category enum | PERSONAL, WORK, DEVELOPMENT, SOCIAL, BANKING, ENTERTAINMENT, OTHER | 1.5 |
| M-21 | Search API | `GET /api/vault/search` by title, username, website (derived query or `@Query`) | 1.5 |
| M-22 | Category filter | `GET /api/vault?category=BANKING` | 1.5 |
| M-23 | Indexes | Index `title`, `category`; **explain why in the README/docs** | 1.5 |
| M-24 | DTO refactor | Request DTO → Service → Entity → DB → Entity → Response DTO. No controller returns an entity/String/Boolean | 2.1 |
| M-25 | Bean Validation | `@NotBlank @NotNull @Email @Size @Pattern` on all inbound requests | 2.2 |
| M-26 | Global exception handler | `@ControllerAdvice` + custom exceptions: UserNotFound, CredentialNotFound, DuplicateEmail, InvalidCredentials, ValidationFailure | 2.3 |
| M-27 | Standard response wrapper | `{ success, message, data }` on every endpoint | 2.3 |
| M-28 | DTO↔Entity mapping | Manual mapper or MapStruct — must understand *why* mapping exists | 2.1 |
| M-29 | Password strength analyzer | `POST /api/password/strength` — length, upper, lower, digits, specials, repeated chars, sequential patterns (1234/abcd), dictionary words → score + level + feedback | 3.1 |
| M-30 | Password generator | `POST /api/password/generate` — configurable length/upper/lower/numbers/symbols, **`SecureRandom` only**, never `java.util.Random`, different output every call | 3.2 |
| M-31 | Transaction management | `@Transactional` on create/update/delete credential | 4.1 |
| M-32 | AuditLog entity | id, action, entityType, entityId, performedBy, timestamp — written **inside the same transaction**; audit failure must roll back the credential change | 4.1 |
| M-33 | N+1 elimination | Fetch strategies for User/Credential/AuditLog, fix ≥1 API with `JOIN FETCH` / `@EntityGraph` / projection, SQL count before vs after, written explanation | 4.4 |
| M-34 | Pagination + sorting + filtering | `GET /api/vault?page&size&sortBy&direction&category&title&username&website`, `Pageable`, response with totalElements/totalPages/currentPage/pageSize/content, **seed ≥50 credentials** | 4.5 |
| M-35 | Password history | `PasswordHistory` (id, credentialId, encryptedPassword, version, createdAt); versions start at 1 and increment; history rows are immutable; only created when the password actually changes | 4.2 |
| M-36 | Reuse prevention | Compare against last 5 decrypted history entries → reject with **409 Conflict** | 4.2 |
| M-37 | Soft delete | `deleted` + `deletedAt` on Credential; delete becomes logical | 4.3 |
| M-38 | Restore / Trash / Permanent | `PUT /api/vault/{id}/restore`, `GET /api/vault/trash`, `DELETE /api/vault/{id}/permanent` (removes credential + history, **keeps audit logs**) | 4.3 |
| M-39 | Deleted-record exclusion | List, get-by-id, search, category filter, update must all ignore soft-deleted rows | 4.3 |
| M-40 | Custom thread pool | `ThreadPoolTaskExecutor` (core/max/queue/prefix) + `@EnableAsync` | 4.6 |
| M-41 | Async background work | `@Async` for email simulation, activity logging, strength recalculation; log thread names; prove request thread ≠ background thread; test concurrency | 4.6 |
| M-42 | Credential sharing | `CredentialShare` (id, credentialId, ownerId, sharedWithUserId, permission, sharedAt, expiresAt, active); READ vs EDIT semantics | 5.1 |
| M-43 | Sharing APIs | `POST /api/share`, `GET /api/share/received`, `PUT /api/share/{id}`, `DELETE /api/share/{id}` | 5.1 |
| M-44 | Sharing authorization | Owner → allow; else check share + permission → allow/deny; unauthorized → **403 Forbidden** | 5.1 |
| M-45 | Sharing business rules | No self-share, only owner shares, no duplicate share, revoke is immediate, deleted/soft-deleted credentials are unshareable and hidden | 5.1 |
| M-46 | Production logging | Remove every `System.out.println`, SLF4J across register/login success/login failure/CRUD/password change/sharing/soft delete/restore/file upload/exceptions, correct levels, **never log secrets** | 4.7 |
| M-47 | Logback config | `logback-spring.xml`: console + file, daily rolling, max size, 7-day retention | 4.7 |
| M-48 | Repo submission process | Fork/clone central repo, branch = your full name, **delete `README.md` and `requirements.txt` before pushing**, never commit to main, build must compile, report branch name to mentor | 9.1 |

### 5.1 The README conflict — resolved
M-23 says *"explain in your README why these fields were indexed."*
M-48 says *"remove README.md before pushing."*
**Resolution:** all written explanations live in `docs/` (`docs/decisions.md`, `docs/db-design.md`).
A root `README.md` exists in your personal repo only and is deleted on the submission branch by
the sync script (§7.3). If the mentor asks for "the README explanation", send `docs/db-design.md`.

---

## 6. Locked technical decisions

Changing any of these requires a new entry in `docs/decisions.md`.

| ID | Decision | Rationale |
|---|---|---|
| D-01 | **Java 21 (LTS)**, Maven | Spring Boot 3.x baseline; LTS through the internship and beyond |
| D-02 | **Spring Boot 3.3.x** | Current stable line; Jakarta namespace, `@ServiceConnection` for tests |
| D-03 | **Monolith, feature-first packages** | Mentor grades layering, not distributed systems. Modular packages give microservice-shaped boundaries with zero ops cost |
| D-04 | **Flyway migrations from day 1**, `ddl-auto=validate` | Reproducible schema across local/Neon/CI and across different AI agents. `ddl-auto=update` silently diverges and is the #1 source of "works on my machine" |
| D-05 | **AES-256-GCM**, random 12-byte IV per record, stored `base64(iv):base64(ciphertext)` | GCM is authenticated encryption; ECB/CBC-without-MAC is a security bug the mentor may probe |
| D-06 | Master AES key from env var (base64, 32 bytes), never in git | Key material in source is an automatic fail |
| D-07 | **BCrypt** (`BCryptPasswordEncoder`, strength 10) for account passwords | Mentor-mandated; one-way for login, two-way (AES) for vault items — know the difference |
| D-08 | **jjwt 0.12.x** for JWT (HS256), access token 15 min, refresh token 7 days | 0.12 API is `Jwts.builder().subject(...).signWith(key)`; older tutorials use the deprecated 0.9 API — do not mix |
| D-09 | Column name `encrypted_password` / field `encryptedPassword` | Explicit mentor requirement (M-08) |
| D-10 | **MapStruct** for DTO↔Entity (with `lombok-mapstruct-binding`) | Compile-time, no reflection; mentor accepts manual or MapStruct |
| D-11 | Uniform `ApiResponse<T>` envelope on every endpoint | Mentor requirement M-27; also makes the React layer trivial |
| D-12 | **Spring Data `Specification`** for dynamic vault filtering | Combines page + sort + N optional filters without query explosion (M-34) |
| D-13 | **Redis** for refresh-token/JWT denylist + vault-list cache | PDF requires Redis; a denylist is the honest use case for stateless JWT logout |
| D-14 | **React 18 + Vite + JavaScript** (not TypeScript) | PDF's stack list says JavaScript; matches evaluation criteria. Tailwind + Redux Toolkit + Axios + React Router per spec |
| D-15 | **Render + Neon + Upstash**, all free tiers | Zero budget. Render free web services cold-start after inactivity — acceptable for a demo, mention it in the demo script |
| D-16 | Testing: JUnit 5 + Mockito (unit) + **Testcontainers** (integration) | Integration tests hit real PostgreSQL, not H2 — H2 hides Postgres-specific SQL bugs |
| D-17 | `springdoc-openapi` for Swagger UI | Free API documentation; strong demo artifact |
| D-18 | Reports: **Apache POI** (Excel) + **OpenPDF** (PDF) | OpenPDF is LGPL/MPL — iText 7 is AGPL and unsuitable |

---

## 7. Repository & branch strategy

### 7.1 Two repositories
| Repo | Purpose | Contents |
|---|---|---|
| **Personal** — `github.com/<you>/securevault` | Real development history, full docs, CI | Everything: `backend/`, `frontend/`, `docs/`, `README.md`, `.github/` |
| **Central** — `springboardmentor1295d-arch/SecureVault` | Graded submission only | Your branch `<your-full-name>`, code only, **no `README.md`, no `requirements.txt`** |

### 7.2 Branch model (personal repo)
```
main                  ← always green, always deployable
 └── phase/<n>-<slug> ← e.g. phase/1-auth-vault-core
      └── (commits per session, squash-merged into main at phase end)
```
Tag each milestone: `git tag milestone-1 && git push --tags`.

### 7.3 Submission flow (run only when a major task block is complete)
```bash
# from personal repo, on green main
git remote add central https://github.com/springboardmentor1295d-arch/SecureVault.git
git fetch central
git checkout -B <your-full-name> central/main     # start from THEIR main
# copy backend/ (and frontend/ when relevant) over, then:
rm -f README.md requirements.txt
mvn -f backend/pom.xml clean verify               # must pass before pushing
git add -A && git commit -m "SecureVault: <task block> — <your name>"
git push central <your-full-name>
# then message the mentor with the branch name
```
**Hard rules:** never `git push central main`; never push a non-compiling build; never push
`.env`, key material, or `application-local.yml`.

### 7.4 Commit convention
```
<type>(<scope>): <imperative summary>     # feat|fix|refactor|test|docs|chore|perf
```
Example: `feat(vault): add AES-GCM encryption to credential create flow`
Every session ends with **one** commit that includes the `docs/progress.md` update.

---

## 8. Directory structure

```
securevault/
├── CLAUDE.md                     # pointer file → docs/ai/CONTEXT.md
├── AGENTS.md                     # pointer file (Antigravity / Codex / generic agents)
├── GEMINI.md                     # pointer file (Gemini CLI)
├── README.md                     # personal repo only — deleted on submission branch
├── docker-compose.yml            # postgres + redis for local dev
├── .env.example                  # every variable, no real values
├── .gitignore
├── docs/
│   ├── securevault_master.md     # THIS FILE
│   ├── progress.md               # append-only session log + current state
│   ├── guide.md                  # how to run, architecture, request flow
│   ├── decisions.md              # ADR log
│   ├── db-design.md              # tables, columns, keys, index rationale (M-04, M-23)
│   ├── api-contract.md           # endpoint index + payload examples
│   ├── evidence/                 # screenshots, SQL logs, Postman exports per milestone
│   ├── erd/                      # dbdiagram DSL + exported ERD image
│   └── ai/
│       ├── CONTEXT.md            # what every agent reads first
│       └── CONVENTIONS.md        # hard coding rules
├── postman/
│   └── SecureVault.postman_collection.json
├── scripts/
│   ├── context-pack.sh           # bundles docs for pasting into a chat UI
│   └── sync-submission.sh        # §7.3 automated
├── backend/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/securevault/
│       ├── SecureVaultApplication.java
│       ├── config/            # SecurityConfig, AsyncConfig, RedisConfig, OpenApiConfig, CorsConfig
│       ├── common/
│       │   ├── response/      # ApiResponse, PagedResponse
│       │   ├── exception/     # custom exceptions + GlobalExceptionHandler + ErrorCode
│       │   ├── audit/         # AuditLog entity/service/aspect
│       │   └── util/
│       ├── security/          # JwtService, JwtAuthenticationFilter, UserPrincipal, crypto/AesEncryptionService
│       ├── user/              # entity, repository, service, controller, dto, mapper
│       ├── vault/             # Credential, PasswordHistory, Category, specifications
│       ├── password/          # strength analyzer + generator
│       ├── sharing/           # CredentialShare
│       ├── notification/
│       ├── monitoring/        # login attempts, anomaly detection, alerts
│       ├── report/            # PDF/Excel export
│       └── admin/
│   └── src/main/resources/
│       ├── application.yml           # shared
│       ├── application-local.yml     # gitignored
│       ├── application-prod.yml
│       ├── logback-spring.xml
│       └── db/migration/V1__init.sql ...
└── frontend/
    ├── package.json
    ├── Dockerfile
    └── src/
        ├── api/          # axios instance + interceptors + endpoint modules
        ├── app/          # store.js, router.jsx
        ├── features/     # auth, vault, password, sharing, dashboard, admin
        ├── components/   # shared UI
        ├── hooks/
        └── utils/
```

---

## 9. Coding conventions (hard rules)

These are copied verbatim into `docs/ai/CONVENTIONS.md` and are binding on every agent.

**Layering**
1. `Controller` — HTTP only. No business logic, no repository access, no entity in or out.
2. `Service` — all business logic, transactions, encryption calls, audit writes.
3. `Repository` — Spring Data interfaces only. No logic.
4. Cross-layer shortcuts are rejected in review.

**API contract**
- Every response is wrapped:
  ```json
  { "success": true, "message": "Credential created successfully", "data": { }, "timestamp": "2026-01-01T10:00:00Z" }
  ```
- Every error:
  ```json
  { "success": false, "message": "Credential not found", "errorCode": "CREDENTIAL_NOT_FOUND",
    "data": null, "errors": [{ "field": "title", "message": "must not be blank" }],
    "timestamp": "2026-01-01T10:00:00Z" }
  ```
- Error codes are a Java enum: `USER_NOT_FOUND`, `DUPLICATE_EMAIL`, `INVALID_CREDENTIALS`,
  `CREDENTIAL_NOT_FOUND`, `VALIDATION_FAILED`, `ACCESS_DENIED`, `PASSWORD_REUSED`,
  `SHARE_ALREADY_EXISTS`, `SELF_SHARE_NOT_ALLOWED`, `TOKEN_EXPIRED`, `TOKEN_INVALID`,
  `MFA_REQUIRED`, `MFA_INVALID`, `INTERNAL_ERROR`.
- HTTP codes: 200 ok · 201 created · 204 no content · 400 validation · 401 unauthenticated ·
  403 authorised-but-forbidden · 404 missing · 409 conflict/reuse/duplicate · 500 unexpected.

**Naming**
- Entities singular (`Credential`), tables plural snake_case (`credentials`).
- DTOs: `CredentialCreateRequest`, `CredentialResponse`, `CredentialUpdateRequest`.
- Services: interface `CredentialService` + `CredentialServiceImpl` (mentor-friendly, testable).
- Booleans read as questions: `isDeleted()`, `hasPermission()`.

**Security**
- Never log a password, secret, token, or decrypted value — not even at DEBUG.
- Never return `encryptedPassword` in a list response; only on explicit single-credential reveal.
- Every vault operation resolves the user from the **JWT principal**, never from a request body.
- Validate ownership *or* an active share before every read/write.

**Persistence**
- Every schema change is a new Flyway file: `V<n>__<snake_case_description>.sql`. Never edit an applied migration.
- All `@ManyToOne` are `FetchType.LAZY`. `EAGER` requires a written justification.
- Every write path that touches two tables is `@Transactional`.

**Never do**
- `java.util.Random` for anything security-related — `SecureRandom` only.
- Return an entity from a controller.
- Catch an exception and swallow it.
- Add a dependency without recording it in `docs/decisions.md`.
- `git push --force` to a shared branch.

---

## 10. Data model

Full target model. Phases add tables progressively; the ERD is regenerated each time.

### `users`
| Column | Type | Notes |
|---|---|---|
| id | BIGSERIAL PK | |
| full_name | VARCHAR(100) NOT NULL | |
| email | VARCHAR(150) NOT NULL UNIQUE | indexed |
| password_hash | VARCHAR(60) NOT NULL | BCrypt |
| role | VARCHAR(20) NOT NULL | USER, TEAM_MEMBER, ADMIN |
| mfa_enabled | BOOLEAN DEFAULT false | |
| mfa_secret | VARCHAR(255) | encrypted TOTP secret |
| account_locked | BOOLEAN DEFAULT false | |
| failed_login_attempts | INT DEFAULT 0 | |
| created_at / updated_at | TIMESTAMPTZ | |

### `credentials`
| Column | Type | Notes |
|---|---|---|
| id | BIGSERIAL PK | |
| user_id | BIGINT FK → users | LAZY |
| title | VARCHAR(150) NOT NULL | **indexed** |
| username | VARCHAR(150) | |
| encrypted_password | TEXT NOT NULL | `base64(iv):base64(ct)` — never plaintext |
| website_url | VARCHAR(255) | |
| notes | TEXT | encrypted if sensitive |
| category | VARCHAR(30) NOT NULL | enum, **indexed** |
| favorite | BOOLEAN DEFAULT false | |
| strength_score | SMALLINT | cached analyzer result |
| deleted | BOOLEAN DEFAULT false NOT NULL | soft delete |
| deleted_at | TIMESTAMPTZ | |
| created_at / updated_at | TIMESTAMPTZ | |

Composite index: `(user_id, deleted)` — every list query filters on both.

### `password_history`
`id` · `credential_id` FK → credentials (CASCADE) · `encrypted_password` TEXT · `version` INT ·
`created_at` TIMESTAMPTZ. Unique `(credential_id, version)`. Immutable rows.

### `credential_shares`
`id` · `credential_id` FK · `owner_id` FK → users · `shared_with_user_id` FK → users ·
`permission` VARCHAR(10) (READ|EDIT) · `shared_at` · `expires_at` NULL · `active` BOOLEAN.
Unique `(credential_id, shared_with_user_id)` where active — enforces "no duplicate share".

### `audit_logs`
`id` · `action` VARCHAR(50) · `entity_type` VARCHAR(50) · `entity_id` BIGINT ·
`performed_by` BIGINT · `timestamp` TIMESTAMPTZ · `ip_address` · `user_agent` · `details` TEXT.
**Never deleted**, even on permanent credential delete.

### `login_attempts`
`id` · `email` · `successful` BOOLEAN · `ip_address` · `user_agent` · `attempted_at` · `failure_reason`.

### `devices` (sessions)
`id` · `user_id` FK · `device_fingerprint` · `device_name` · `ip_address` · `last_seen_at` · `trusted` BOOLEAN.

### `refresh_tokens`
`id` · `user_id` FK · `token_hash` · `expires_at` · `revoked` BOOLEAN · `created_at`.
(Redis holds the fast denylist; Postgres holds the durable record.)

### `notifications`
`id` · `user_id` FK · `type` · `title` · `message` · `read` BOOLEAN · `created_at`.

### Relationship summary
```
User 1 ──< Credential 1 ──< PasswordHistory
User 1 ──< AuditLog
User 1 ──< Device
User 1 ──< Notification
User 1 ──< RefreshToken
Credential 1 ──< CredentialShare >── 1 User (shared_with)
```

---

## 11. API surface

Complete target surface. Each phase implements a slice; `docs/api-contract.md` tracks live status.

**Auth** — `POST /api/auth/register` · `POST /api/auth/login` · `POST /api/auth/refresh` ·
`POST /api/auth/logout` · `POST /api/auth/mfa/setup` · `POST /api/auth/mfa/verify` ·
`POST /api/auth/forgot-password` · `POST /api/auth/reset-password`

**Vault** — `POST /api/vault` · `GET /api/vault` (page, size, sortBy, direction, category, title, username, website) ·
`GET /api/vault/{id}` · `PUT /api/vault/{id}` · `DELETE /api/vault/{id}` (soft) ·
`GET /api/vault/search?q=` · `GET /api/vault/trash` · `PUT /api/vault/{id}/restore` ·
`DELETE /api/vault/{id}/permanent` · `PUT /api/vault/{id}/favorite` · `GET /api/vault/{id}/history`

**Password** — `POST /api/password/strength` · `POST /api/password/generate`

**Sharing** — `POST /api/share` · `GET /api/share/received` · `GET /api/share/sent` ·
`PUT /api/share/{id}` · `DELETE /api/share/{id}`

**Analytics** — `GET /api/dashboard/summary` · `GET /api/dashboard/password-health` ·
`GET /api/dashboard/recent-activity` · `GET /api/dashboard/alerts`

**Audit & monitoring** — `GET /api/audit/logs` · `GET /api/monitoring/login-attempts` ·
`GET /api/monitoring/devices`

**Reports** — `GET /api/reports/password-health?format=pdf|excel` · `GET /api/reports/audit?format=`

**Admin** — `GET /api/admin/users` · `PUT /api/admin/users/{id}/status` · `GET /api/admin/stats`

**Ops** — `GET /actuator/health` · `GET /swagger-ui.html`

---

## 12. Security model

| Concern | Mechanism |
|---|---|
| Account password | BCrypt one-way hash, strength 10, never retrievable |
| Vault secret | AES-256-GCM two-way, per-record random IV, key from env |
| Transport | HTTPS in production (Render terminates TLS) |
| Session | Stateless JWT (HS256). Access 15 min, refresh 7 days, rotation on refresh |
| Logout | Refresh token revoked in Postgres + access `jti` denylisted in Redis until natural expiry |
| Authorisation | Ownership check first, then active-share permission check, else **403** |
| MFA | TOTP (RFC 6238), 30 s step, QR provisioning URI, backup codes hashed |
| Brute force | Failed-attempt counter → lock after 5, exponential backoff, audit entry per attempt |
| Injection | JPA parameter binding only; no string-concatenated JPQL/SQL |
| Secrets | Env vars only; `.env` and `application-local.yml` are gitignored; `.env.example` documents keys |

**Sharing authorisation matrix**

| Actor | View | Update | Delete | Re-share | Permanent delete |
|---|---|---|---|---|---|
| Owner | ✅ | ✅ | ✅ | ✅ | ✅ |
| Share: READ | ✅ | ❌ 403 | ❌ 403 | ❌ 403 | ❌ 403 |
| Share: EDIT | ✅ | ✅ | ❌ 403 | ❌ 403 | ❌ 403 |
| Unrelated user | ❌ 403 | ❌ 403 | ❌ 403 | ❌ 403 | ❌ 403 |

Revoked or expired shares behave exactly like "unrelated user", immediately.

---

## 13. Environment & configuration

`.env.example` (committed; real `.env` never is):
```
# --- database ---
DB_URL=jdbc:postgresql://localhost:5432/securevault
DB_USERNAME=postgres
DB_PASSWORD=
# --- security ---
JWT_SECRET=                 # >= 32 bytes, base64
JWT_ACCESS_EXPIRY_MS=900000
JWT_REFRESH_EXPIRY_MS=604800000
AES_SECRET_KEY=             # exactly 32 bytes, base64 encoded
# --- redis ---
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
# --- mail (optional until Phase 5) ---
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=
MAIL_PASSWORD=              # Gmail app password, not the account password
# --- app ---
APP_CORS_ORIGINS=http://localhost:5173
SERVER_PORT=8080
```

**Profiles:** `local` (Docker Postgres/Redis, SQL logging on, Swagger on) ·
`test` (Testcontainers) · `prod` (Neon + Upstash, SQL logging off, Swagger optional).

**Production notes:** Render injects `PORT` — set `server.port=${PORT:8080}`.
Neon requires `?sslmode=require` on the JDBC URL. Upstash Redis requires TLS (`rediss://`).

---

## 14. Documentation system & session protocol

Three living documents. Keeping them current is **part of every session's definition of done**.

### `docs/progress.md` — the state file
```markdown
# SecureVault — Progress Log

## CURRENT STATE            <!-- rewritten every session, always at the top -->
- Phase: 1 — Auth & Vault Core
- Last session: S1.3 (AES + credential create/read)
- Build: green | Tests: 12 passing | Migrations applied: V1..V4
- Working branch: phase/1-auth-vault-core
- Next session: S1.4 — update/delete + ownership
- Open blockers: none

## NEXT UP                  <!-- ordered queue, 3-5 items -->
1. S1.4 update/delete + ownership verification
2. S1.5 category enum, search, indexes
3. S1.6 Milestone 1 evidence pack

---
## SESSION LOG              <!-- append-only, newest at the bottom -->
### S1.3 — 2026-01-14 — AES encryption + credential create/read
**Mentor tasks:** M-08, M-09, M-10
**Done:** AesEncryptionService (GCM, random IV); Credential entity + V3 migration;
POST /api/vault; GET /api/vault/{id} with decrypt; Postman requests added.
**Files:** security/crypto/AesEncryptionService.java, vault/*, db/migration/V3__credentials.sql
**Decisions:** stored as base64(iv):base64(ct) — see ADR-005
**Verified:** DB row shows ciphertext; GET returns original password; 2 credentials for same user
**Blockers:** none
**Commit:** feat(vault): add AES-GCM encrypted credential create and read
```

### `docs/guide.md` — the operator manual
Sections, in order: Prerequisites · First-time setup · Daily run commands · Environment
variables · Architecture overview · Request flow (end-to-end, with the DispatcherServlet →
Controller → Service → Repository → Hibernate → PostgreSQL chain the mentor drew) ·
Module map · Database schema · API index · Testing · Deployment · Troubleshooting.
**Rewrite the affected section in the same session that changes behaviour** — a stale guide is worse than none.

### `docs/decisions.md` — ADR log
```markdown
### ADR-005 — AES ciphertext storage format
**Date:** 2026-01-14 · **Status:** accepted
**Context:** GCM needs a unique IV per encryption; it must be recoverable at decrypt time.
**Decision:** store `base64(iv):base64(ciphertext)` in a single TEXT column.
**Alternatives:** separate iv column (extra migration, no benefit); fixed IV (**insecure**).
**Consequences:** format change requires a data migration; documented in db-design.md.
```

### Session protocol (every single session, no exceptions)
1. **Read** `docs/progress.md` → CURRENT STATE, then this file's relevant section.
2. **State the plan** in 3–6 bullets before writing code. Wait for approval on anything
   touching security, schema, or a locked decision.
3. **Implement** — smallest correct change, following §9.
4. **Verify** — build passes, endpoints tested in Postman, evidence captured if it's a graded task.
5. **Document** — update `progress.md` (CURRENT STATE + new log entry), plus `guide.md`,
   `api-contract.md`, `db-design.md`, `decisions.md` if affected.
6. **Commit** once, using §7.4, including the doc updates.

---

## 15. Multi-AI consistency protocol

You will switch between Claude Code, Gemini, ChatGPT/gpt-oss, and Antigravity's own agent.
Consistency comes from files in the repo, not from any tool's memory.

**Pointer files** — three tiny files at the repo root, each ~6 lines, all saying the same thing:
`CLAUDE.md`, `AGENTS.md`, `GEMINI.md` →
> "You are working on SecureVault. Before doing anything, read `docs/ai/CONTEXT.md`,
> `docs/progress.md` (CURRENT STATE), and `docs/securevault_master.md` §5, §6, §9.
> Follow `docs/ai/CONVENTIONS.md` exactly. Never change a locked decision without asking."

**`docs/ai/CONTEXT.md`** — a 1-page briefing: what the project is, the stack, where things live,
what phase we're in, the 10 rules that matter most, and what "done" means.

**`docs/ai/CONVENTIONS.md`** — §9 of this document, verbatim.

**For chat UIs without repo access** (ChatGPT web, Gemini web), run:
```bash
./scripts/context-pack.sh          # concatenates CONTEXT.md + CONVENTIONS.md +
                                   # progress.md CURRENT STATE + api-contract.md
                                   # into build/context-pack.md for pasting
```
Add the specific files you're editing after the pack. Paste the result back only after
reviewing it against §9 — code from a chat UI is the most likely place for drift to enter.

**Drift control**
- Formatting is enforced mechanically (`spotless` or `.editorconfig` + Prettier), so no agent
  can start a style argument in a diff.
- Any agent that adds a dependency, changes a package layout, or alters the response envelope
  must write an ADR. No ADR → revert.
- At each phase end, run a **consistency audit** (prompt P-AUDIT in the prompts file):
  package layout, response envelope, exception handling, validation, logging, naming.

---

## 16. Phase & session map

Sessions are **atomic**: each one starts from a green build and ends with a green build,
a commit, and updated docs. Chain as many as your available time allows; stopping between
any two is always safe.

**Milestone mapping** — M1 (Wk 1–2): Phases 0–1 · M2 (Wk 3–4): Phases 2–4 ·
M3 (Wk 5–6): Phases 5–6 · M4 (Wk 7–8): Phases 6–9.

**Catch-up route (you are at day zero):** Phases 0 and 1 are the bottleneck — everything
else depends on working auth + encrypted vault. Push hard through S0.1→S1.6 first; from
Phase 2 onward the sessions are genuinely independent and can be picked off in any spare hour.

### Phase 0 — Workspace & foundations
| Session | Title | Mentor | Acceptance |
|---|---|---|---|
| S0.1 | Repo, docs system, Docker services, Spring Boot skeleton | — | `mvn spring-boot:run` starts; `/actuator/health` returns UP; Postgres + Redis containers up; all doc files exist |
| S0.2 | Product decomposition + architecture reasoning writeups | M-01, M-02 | `docs/decomposition.md` (Feature/Why table, ≥25 features) and `docs/architecture.md` (layer diagram + where JWT/AES/Redis/audit/email sit, with reasoning) |
| S0.3 | Schema design, ERD, Flyway baseline | M-03, M-04, M-05 | DB `securevault` exists; `docs/db-design.md` complete with index rationale; ERD exported to `docs/erd/`; `V1__init.sql` applies cleanly |

### Phase 1 — Milestone 1: auth + vault core
| Session | Title | Mentor | Acceptance |
|---|---|---|---|
| S1.1 | User entity + registration + BCrypt | M-06, M-07 | Postman creates a user; row visible in `users`; duplicate email → 409; two identical passwords → different hashes (screenshot) |
| S1.2 | Spring Security + JWT + login | M-15..M-19 | Login returns JWT; `/api/vault/**` without token → 401; with token → 200; expired token → 401 |
| S1.3 | Credential entity + AES-GCM + create/read | M-08, M-09, M-10 | DB column holds ciphertext only; GET returns the original password; multiple credentials per user |
| S1.4 | Update, delete, ownership verification | M-11, M-12, M-13 | Password re-encrypted only when changed; deleting one row leaves others intact; other user's credential → 403 |
| S1.5 | Category enum, search, filter, indexes | M-20..M-23 | Partial-title search works; category filter works; empty result → empty list not error; index rationale written |
| S1.6 | Milestone 1 evidence pack | M-14 | Every checklist line ticked with a screenshot or Postman run in `docs/evidence/milestone-1/` |

### Phase 2 — Production-grade API refactor
| Session | Title | Mentor | Acceptance |
|---|---|---|---|
| S2.1 | DTO layer + MapStruct mappers | M-24, M-28 | No controller signature references an entity; mappers unit-tested |
| S2.2 | Bean Validation across all requests | M-25 | Invalid payload → 400 with per-field messages |
| S2.3 | Custom exceptions + `@ControllerAdvice` + `ApiResponse` | M-26, M-27 | Every endpoint returns the envelope; every error path has a code; no stack trace leaks to the client |
| S2.4 | Sweep + Postman regression | — | Full collection re-run green; `docs/api-contract.md` regenerated |

### Phase 3 — Password intelligence
| Session | Title | Mentor | Acceptance |
|---|---|---|---|
| S3.1 | Strength analyzer | M-29 | Detects length, character classes, repeats, sequences (1234/abcd/qwerty), common dictionary words; returns score + level + actionable feedback |
| S3.2 | Generator with `SecureRandom` | M-30 | Config respected; guaranteed ≥1 char of each enabled class; 1000 calls → 1000 distinct outputs; `java.util.Random` absent from the codebase |
| S3.3 | Entropy + vault integration | — | Strength computed and cached on credential save/update; exposed in responses |

### Phase 4 — Data integrity, performance, operations
| Session | Title | Mentor | Acceptance |
|---|---|---|---|
| S4.1 | `@Transactional` + AuditLog with rollback proof | M-31, M-32 | Forced audit failure rolls back the credential change — demonstrated and screenshotted |
| S4.2 | Password history + reuse prevention | M-35, M-36 | Versions increment from 1; history only on password change; reuse of any of last 5 → **409** |
| S4.3 | Soft delete, restore, trash, permanent delete | M-37, M-38, M-39 | Deleted rows invisible to list/get/search/filter/update; permanent delete removes credential + history but **keeps audit logs** |
| S4.4 | N+1 elimination | M-33 | SQL query count before/after captured in `docs/evidence/`; fetch strategy table written |
| S4.5 | Pagination, sorting, dynamic filtering + 50-row seed | M-34 | All params combinable; response includes totalElements/totalPages/currentPage/pageSize/content |
| S4.6 | Async thread pool + background tasks | M-40, M-41 | Logs prove request thread ≠ background thread; concurrent Postman run shows thread reuse |
| S4.7 | SLF4J + logback-spring.xml | M-46, M-47 | Zero `System.out.println`; rolling daily files with size cap and 7-day retention; no secret ever logged |
| S4.8 | Milestone 2 evidence pack | — | `docs/evidence/milestone-2/` complete |

### Phase 5 — Sharing, sessions, platform hardening
| Session | Title | Mentor | Acceptance |
|---|---|---|---|
| S5.1 | Credential sharing + permission model | M-42..M-45 | Full authorisation matrix (§12) demonstrated including 403 cases and immediate revoke |
| S5.2 | Refresh tokens, logout, Redis denylist | — | Refresh rotates; logout invalidates immediately; replayed old refresh → 401 |
| S5.3 | Redis caching + invalidation | — | Vault list cached per user; any write evicts; cache-hit visible in logs |
| S5.4 | MFA (TOTP) + device/session tracking | — | QR provisioning works in an authenticator app; login requires the code; backup codes single-use |
| S5.5 | Security monitoring & anomaly detection | — | Failed attempts recorded; lock after 5; new-device and impossible-pattern alerts raised |
| S5.6 | Notifications + async email | — | Login alert, share notification, password-expiry alert delivered off the request thread |
| S5.7 | Analytics dashboard APIs | — | Password-health score, credential overview, recent activity, alerts — all computed server-side |
| S5.8 | OpenAPI/Swagger + admin endpoints | — | `/swagger-ui.html` documents every endpoint; admin routes are ADMIN-only (403 otherwise) |

### Phase 6 — React frontend
| Session | Title | Acceptance |
|---|---|---|
| S6.1 | Vite scaffold, Tailwind, router, axios interceptors, Redux store | App boots; 401 interceptor triggers refresh-then-retry |
| S6.2 | Auth screens + protected routes + MFA | Register/login/logout/MFA flows work against the live API |
| S6.3 | Vault UI — list, search, filter, pagination, CRUD, reveal/copy | Every backend vault capability reachable from the UI |
| S6.4 | Generator + live strength meter | Meter updates as you type; generated password can be saved directly |
| S6.5 | Sharing UI + trash/restore | Share, change permission, revoke, restore — all visible |
| S6.6 | Dashboard & analytics | Health score, weak/reused/old password lists, activity feed, alerts |
| S6.7 | Admin console + audit log viewer | Filterable audit table, user management |
| S6.8 | Polish | Responsive, loading/empty/error states, toasts, no console errors |

### Phase 7 — Testing & quality
| S7.1 | Service unit tests (Mockito) — encryption, strength, generator, sharing rules |
| S7.2 | Integration tests (`@SpringBootTest` + Testcontainers PostgreSQL) — auth and vault journeys |
| S7.3 | Security test matrix — 401/403 for every protected route, ownership and share cases |
| S7.4 | Frontend tests (React Testing Library) — auth form, vault list, strength meter |
| S7.5 | JaCoCo coverage report + gap closure on service layer |

### Phase 8 — Deployment
| S8.1 | Multi-stage Dockerfiles (backend + frontend) and full `docker-compose` |
| S8.2 | Neon Postgres + Upstash Redis provisioned; `prod` profile; migrations applied remotely |
| S8.3 | Render deploy — backend Docker web service (`PORT` binding, health check) + frontend static site; CORS wired |
| S8.4 | GitHub Actions CI — build, test, (optional) image publish |
| S8.5 | Reports & export module — PDF (OpenPDF) + Excel (Apache POI) |
| S8.6 | Final documentation — `guide.md` complete, architecture diagram, demo script, presentation deck |

### Phase 9 — Submission & demo
| S9.1 | Central-repo sync: branch, README/requirements removal, `clean verify`, push, notify mentor |
| S9.2 | Demo rehearsal + evidence pack: full user journey in under 8 minutes, fallback screenshots for cold-start delays |

---

## 17. Milestone evidence checklist

The mentor grades artifacts, not vibes. Capture as you go into `docs/evidence/milestone-<n>/`.

**Milestone 1** — Spring Boot startup log · pgAdmin showing `securevault` tables · ERD image ·
Postman: register success, duplicate email, login returning JWT, vault 401 without token,
vault 200 with token · DB row showing a BCrypt hash · DB row showing AES ciphertext ·
two identical passwords hashing differently · search + category filter results ·
completed M-14 checklist with each line ticked.

**Milestone 2** — Before/after response bodies showing the `ApiResponse` envelope ·
validation error response · global exception response · strength API sample · generator
producing different outputs for identical config · audit-log rollback demonstration ·
password version rows in `password_history` · reuse rejected with 409 · soft delete /
trash / restore / permanent delete sequence · search excluding deleted rows ·
SQL query counts before vs after N+1 fix · paginated response · thread-name logs proving
async execution · rolling log files on disk.

**Milestone 3** — Sharing matrix (owner, READ, EDIT, revoked, unauthorised 403) ·
MFA QR + successful challenge · Redis cache hit/miss logs · security alerts ·
dashboard screenshots · Swagger UI.

**Milestone 4** — Live Render URLs (frontend + backend health) · Docker build output ·
CI run green · test report + coverage · exported PDF and Excel reports ·
final `guide.md` · presentation deck · recorded end-to-end demo.

---

## 18. Deployment architecture

```
Browser
  │ HTTPS
  ▼
Render Static Site  (frontend/dist, React SPA)
  │ HTTPS  /api/**
  ▼
Render Web Service  (backend Docker image, Spring Boot, PORT injected)
  ├──► Neon PostgreSQL   (sslmode=require, Flyway migrations on boot)
  └──► Upstash Redis     (TLS, token denylist + cache)
```

Free-tier realities to plan around, not discover during the demo:
- Render free web services **sleep after inactivity**; the first request can take ~50 s.
  Warm the service 5 minutes before demoing, and say so in the demo script.
- Neon free projects **auto-suspend**; the first query wakes them (a few seconds).
- Keep migrations fast — they run on every cold start.
- Set `APP_CORS_ORIGINS` to the exact Render static-site domain; wildcards with credentials fail.
- Never let Flyway run `clean` in prod (`spring.flyway.clean-disabled=true`).

---

## 19. Risk register & known traps

| Risk | Mitigation |
|---|---|
| Behind the mentor's stream | Catch-up route in §16; ship Track A only until Phase 4 is done |
| AES key lost or rotated | Key in env + backed up privately; rotation requires a re-encryption migration — plan it, never improvise |
| `ddl-auto=update` drift | Locked to `validate` + Flyway (D-04) |
| Committing a secret | `.gitignore` from S0.1; `git-secrets`/pre-commit hook optional; never paste real keys into a chat UI |
| jjwt API mismatch | 0.12.x only; ignore 0.9-era tutorials (`Jwts.parser().setSigningKey(...)`) |
| MapStruct + Lombok silently generating empty mappers | Add `lombok-mapstruct-binding` to the annotation processor path |
| Flyway 10 with PostgreSQL | Needs `flyway-database-postgresql` alongside `flyway-core` |
| Testcontainers without Docker running | Guard integration tests behind a profile; skip in CI if Docker unavailable |
| Lazy-loading exception in JSON serialisation | DTOs everywhere (Phase 2) — never serialise an entity |
| Free-tier cold start during evaluation | Warm-up ritual + recorded backup demo |
| AI agents drifting apart | §15 pointer files + phase-end consistency audit (P-AUDIT) |
| Losing context mid-project | `docs/progress.md` updated every session — treat it as non-optional |

---

## 20. Definition of done

**Session DoD** — build green · behaviour manually verified · evidence captured if graded ·
`progress.md` CURRENT STATE + log entry updated · affected docs updated · one clean commit.

**Phase DoD** — all sessions done · Postman collection re-run green · consistency audit passed ·
`guide.md` accurate · phase branch squash-merged into `main`.

**Project DoD** — all Track A tasks complete · frontend covers every backend capability ·
tests green with meaningful service-layer coverage · deployed and reachable ·
documentation complete · submission branch pushed and acknowledged · demo rehearsed.

---

## 21. Glossary

**AES-GCM** — authenticated symmetric encryption; provides confidentiality *and* tamper detection.
**BCrypt** — deliberately slow one-way password hash with a built-in per-hash salt.
**JWT** — signed, stateless bearer token carrying identity claims.
**Denylist** — short-lived record of tokens revoked before their natural expiry.
**DTO** — data transfer object; the API's shape, decoupled from the database's shape.
**N+1** — one query for a list plus one per element; fixed with joins, entity graphs, or projections.
**Soft delete** — flagging a row as deleted instead of removing it, so it can be restored and audited.
**TOTP** — time-based one-time password; the 6-digit code in an authenticator app.
**ADR** — architecture decision record; a dated, immutable note explaining *why*.
