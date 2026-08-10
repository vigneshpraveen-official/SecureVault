# SecureVault — Product Decomposition (M-01)

Session S0.2. This is the reasoning behind *why each feature exists*, not a restatement of
what it does. Every "why" is a user or security justification defensible in a viva.
Priority: **Core** (the product fails its purpose without it) · **Important** (expected of
a credible password manager, graded by the mentor) · **Nice** (spec-completeness / demo
polish, Track B). Milestone follows master §16's phase-to-milestone mapping.

| # | Feature | Why does it exist? | Priority | Milestone |
|---|---|---|---|---|
| 1 | User registration | An unauthenticated vault is a public vault — every other feature depends on knowing whose data this is. | Core | M1 |
| 2 | BCrypt password hashing | If the DB is ever read (backup leak, SQL injection, insider), a reversible or unsalted hash turns one breach into every user's account being compromised. BCrypt makes cracking each password individually slow even with the hash exposed. | Core | M1 |
| 3 | JWT login (access + refresh) | REST is stateless by design; without a bearer credential the server can't tell repeat requests from the same user apart from a stranger, and session-in-DB lookups on every request don't scale as cheaply as a signed token the server can verify without a round trip. | Core | M1 |
| 4 | Stateless JWT auth filter | Every protected endpoint needs the same "who is this and are they real" check; putting it in one filter means no controller can forget it, and it runs before business logic so an invalid token never touches the database. | Core | M1 |
| 5 | Duplicate-email rejection | Two accounts sharing an email breaks "forgot password" and makes ownership ambiguous — the email is the only thing a user reliably remembers to log back in with. | Core | M1 |
| 6 | Credential (vault item) CRUD | This *is* the product — a password manager that can't store, read, change, or remove a secret isn't one. | Core | M1 |
| 7 | AES-256-GCM vault encryption | Unlike the account password, the vault password must be recoverable — the user needs to see it again — so it can't be a one-way hash. Without encryption the DB is a plaintext breach waiting to happen. | Core | M1 |
| 8 | Ownership verification on every vault op | A valid JWT proves *who you are*, not what you're allowed to touch — without this check, any logged-in user could read or edit anyone else's credentials by guessing an ID. | Core | M1 |
| 9 | Category classification | A vault with 40+ entries and no grouping becomes unusable to scan; categorisation is the cheapest form of organisation before search exists. | Core | M1 |
| 10 | Search by title/username/website | Scrolling a growing list to find one login doesn't scale past a handful of entries — this is the difference between a vault and a junk drawer. | Core | M1 |
| 11 | Category filter | Complements search for the common case of "show me everything in Banking" without knowing a specific title. | Core | M1 |
| 12 | Favourites / starring | Frequent logins (email, primary bank) deserve to surface above a dozen rarely-used entries — a pure recency or alphabetical list punishes daily-use items. | Nice | M1/M2 |
| 13 | Secure notes on a credential | Not every secret is a username/password pair (recovery codes, PIN, security questions) — forcing those into the wrong field either loses data or misuses the password field. | Important | M1 |
| 14 | DTO layer (never return an entity) | An entity carries lazy-loading proxies, internal fields, and JPA metadata that leak implementation details and cause serialization crashes; a DTO is a deliberate, versioned contract with the client. | Core | M2 |
| 15 | Bean Validation on requests | Rejecting a malformed request at the boundary (400, per-field message) is cheaper and safer than letting bad data reach the service layer and fail in a less predictable way — or worse, silently persist. | Core | M2 |
| 16 | Global exception handling + `ApiResponse` envelope | Without a single handler, error shape depends on which layer happened to throw, giving the frontend a different contract per bug instead of one contract for every response. | Core | M2 |
| 17 | Password strength analyzer | Users chronically underestimate weak passwords; a vault that stores a weak password without saying so is complicit in the breach it enables. | Core | M2 |
| 18 | Password generator (`SecureRandom`) | The strength analyzer only diagnoses the problem — most users can't reliably invent a high-entropy password themselves; generation is the fix, and `java.util.Random` is predictable enough to defeat the purpose. | Core | M2 |
| 19 | `@Transactional` writes | A credential update that touches two tables (e.g. credential + history) must not partially apply — a crash mid-write should leave either the old state or the new one, never a hybrid. | Core | M2 |
| 20 | Audit logging (same transaction) | A security product that can't answer "who accessed what, when" isn't auditable — and an audit record that could silently fail to write (or be written for an action that then rolled back) is worse than no audit log, because it's trusted. | Core | M2 |
| 21 | Password history + reuse prevention | Users cycle back to old passwords when forced to change them, which defeats a mandated rotation entirely — comparing against recent history is the only way to actually enforce "new" means new. | Important | M2 |
| 22 | Soft delete + trash + restore | An accidental delete of a banking credential is a real, high-cost mistake; a recovery window turns a panic into an inconvenience. | Core | M2 |
| 23 | Permanent delete (keeps audit logs) | Users are entitled to actually erase data (trash isn't a real deletion), but the audit trail of *that it was deleted, by whom, when* must survive — otherwise permanent delete becomes a way to erase evidence of misuse. | Important | M2 |
| 24 | Pagination, sorting, dynamic filtering | A vault with 50+ rows returned in one response is slow to transfer and slow to render — pagination bounds both, and sorting/filtering let the user actually navigate that volume. | Core | M2 |
| 25 | N+1 query elimination | An unoptimised list endpoint can silently issue one query per row; at real vault sizes this turns a fast endpoint into a slow one without any code appearing wrong at a glance. | Important | M2 |
| 26 | Async background tasks | Slow, non-critical side effects (email, activity logging, strength recompute) shouldn't make the user wait on the part of the request that actually matters to them. | Important | M2 |
| 27 | Production logging (SLF4J, rolling files) | `System.out.println` disappears when the process restarts and can't be filtered by level or shipped anywhere — a real incident needs a durable, queryable log, not console scrollback. | Core | M2 |
| 28 | Credential sharing (READ/EDIT) | Credentials are sometimes legitimately shared (a family Netflix login, a team's shared tool) — without a sharing feature, users resort to sending plaintext passwords over chat, which is the exact behaviour a vault exists to prevent. | Core | M3 |
| 29 | Sharing authorisation matrix + revoke | A share without enforced permission boundaries (READ can't silently become EDIT) or without immediate revoke isn't access control, it's just a second copy of the secret with no way to take it back. | Core | M3 |
| 30 | Refresh tokens + Redis denylist logout | A 15-minute access token forces frequent re-logins without refresh; but logout must be *immediate*, and a stateless JWT can't be un-signed — the denylist is what makes "logout" mean something before natural expiry. | Important | M3 |
| 31 | Redis vault-list caching | Repeated GETs of the same page of the same user's vault are the single hottest read path in the app — caching it avoids re-querying and re-decrypting-for-display work on every request. | Important | M3 |
| 32 | MFA (TOTP) | A password alone — even a strong one — can be phished or reused elsewhere; a second factor means a leaked password isn't sufficient to access the vault that stores every *other* password. | Important | M3 |
| 33 | Login attempt tracking + lockout | Without a failed-attempt counter, an attacker can brute-force a password with unlimited guesses; lockout after 5 turns an offline-feeling attack surface into one with a real cost. | Important | M3 |
| 34 | Security monitoring / anomaly alerts | A user who never checks their own audit log won't notice a compromise on their own — a new-device or unusual-pattern alert surfaces it to them instead of requiring them to go looking. | Nice | M3 |
| 35 | Notifications + async email | Security-relevant events (new login, received share, password expiring) are only useful if the user learns about them somewhere other than a log table they never open. | Nice | M3 |
| 36 | Analytics dashboard (health score, activity) | "You have 6 reused passwords and 3 that haven't changed in 2 years" is actionable in a way that a raw credential list never surfaces on its own — it turns stored data into a reason to act. | Important | M3 |
| 37 | Swagger / OpenAPI docs | A REST API without live, generated documentation forces every consumer (including the mentor and the React frontend) to read source code to know what's callable — this is also a real evaluation artifact. | Important | M3 |
| 38 | Reports export (PDF/Excel) | Security posture data trapped in a dashboard can't be shared with someone who doesn't have an account (a manager, an auditor) or archived outside the app. | Nice | M4 |
| 39 | Admin console (user mgmt, stats) | Multiple users existing without any moderation surface means a locked-out or abusive account has no operational remedy short of direct DB access. | Nice | M4 |
| 40 | React frontend | An API with no UI is unusable by the actual target user (a non-technical person picking passwords) and untestable as an end-to-end product for the demo. | Core | M3/M4 |
| 41 | Dockerized deployment + CI | The project must be demoed and graded from somewhere other than the developer's own machine; reproducible builds are what make "it works" mean something beyond "on my laptop." | Core | M4 |

## Features we are deliberately NOT building

Per master §3 Track C — explicitly out of scope, not achievable or necessary at zero budget
in 8 weeks, even though the PDF's architecture poster mentions them:

- **Biometric authentication** — requires native device APIs (WebAuthn/platform biometrics)
  far beyond an 8-week backend-first internship scope, and adds no grading value over MFA.
- **SMS/Twilio-based MFA or alerts** — a paid third-party service with no free tier suitable
  for a zero-budget project; TOTP (free, offline, standard) covers the same MFA requirement.
- **Firebase push notifications** — introduces a second cloud vendor and a mobile-app
  assumption the project doesn't have; in-app + email notifications cover the same need.
- **Kafka / RabbitMQ** — a message broker is solving a scale problem (decoupled, high-volume
  event processing) this single-instance monolith doesn't have; `@Async` + a thread pool is
  the honest-sized solution for "don't block the request thread."
- **Kubernetes** — container orchestration for a project deploying one backend instance and
  one frontend static site to Render is pure operational overhead with no matching need.
- **ELK stack** — a full log aggregation/search cluster is disproportionate to a project whose
  logging requirement (M-46/M-47) is satisfied by rolling local/Render log files.
- **AWS S3** — no free tier fits an 8-week project with zero budget; nothing in the current
  spec actually requires object storage (no file uploads are in scope).
- **IP-reputation threat-intel feeds** — a paid/rate-limited external data source for a
  monitoring feature (S5.5) that's adequately demonstrated with failed-login-count and
  new-device heuristics alone.
- **Microservice decomposition** — see `docs/architecture.md`'s "why a modular monolith"
  section: the mentor grades layering and correctness, not distributed-systems operations,
  and this system has no team-scaling or independent-deploy pressure that would justify it.

---
_Session S0.2 — 2026-08-11._
