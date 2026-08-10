# SecureVault — Architecture Reasoning (M-02)

Session S0.2. Reasoned from first principles, not looked up — every answer below states the
reasoning, not just the conclusion, so it survives a follow-up question in a live review.

## Starting point: the naive chain

```
Browser → React → Spring Boot → PostgreSQL
```

This chain is necessary but not sufficient. It says nothing about *who* is allowed to call
Spring Boot, *what* PostgreSQL is allowed to see, or *what happens* when work is slow or
must be provably recorded. Five questions have to be answered by placing components
deliberately, not by default.

## Where does JWT get created, and where is it validated? Why there and not elsewhere?

**Created** in the `AuthenticationService` (Service layer), immediately after a login
request's password is checked against the stored BCrypt hash. Token creation is business
logic — deciding the subject claim, the expiry policy, what gets signed — so it belongs in
the Service tier, not the Controller, which per master §9 does HTTP binding only and must
stay swappable/testable without touching security internals.

**Validated** in a `JwtAuthenticationFilter` sitting early in the Spring Security filter
chain, *before* the request reaches `DispatcherServlet`/`Controller`. This placement is
deliberate: Spring Security's filter chain is exactly the extension point built for
cross-cutting authentication — one filter guarantees every protected endpoint is checked the
same way, and rejecting an invalid token here means it never reaches a controller, never
opens a DB connection, and never runs business logic. Validating inside each controller
(or worse, each service method) would mean the check could be forgotten on a new endpoint,
and the cost of an invalid request wouldn't be paid until much later in the pipeline.

## Where does encryption happen, and why not in the database or the browser?

**In the Service layer** — a dedicated `AesEncryptionService`, called from `CredentialService`
after the incoming DTO is validated but before the entity is handed to the Repository.

**Not in the database:** PostgreSQL has no first-class AES-GCM primitive that unifies IV
generation, key handling, and authenticated encryption the way an application-level service
can. More importantly: if the database performed encryption, the key would have to live in
the database's own configuration — meaning a database-only compromise (a leaked connection
string, an exposed backup, a SQL injection) would directly yield decryptable data. Keeping
encryption in the application tier, with the key only in `AES_SECRET_KEY` (an env var the
database never sees), means a DB-only breach yields ciphertext, not secrets.

**Not in the browser:** client-side encryption would require distributing and rotating the
AES key to every client session, which is strictly worse than a server-held key — it's
exposed to any XSS in the frontend, can't be centrally rotated, and breaks server-side
search/filter on plaintext fields like `title`. It also can't guarantee cryptographically
strong, consistent IV generation across every browser's JS crypto implementation.

## Where does Redis sit, and what specifically does it hold?

Redis sits beside PostgreSQL as a second store, reachable **only from the Service layer**
(never from a Controller or Repository directly), and holds exactly two things:

1. A **denylist of revoked JWT `jti` values** — checked by `JwtAuthenticationFilter` on every
   request, until each token's natural expiry. This is what makes logout mean "immediately
   invalid," not "invalid whenever the 15-minute access token happens to expire anyway."
2. A **short-lived cache of each user's paginated vault list**, keyed by user + query params,
   evicted on any write to that user's vault.

Redis holds nothing durable — `refresh_tokens` in PostgreSQL is the source of truth; Redis is
only the fast, disposable lookup for what's *currently* valid or cached.

## Where are audit logs generated, and why must they share the write transaction?

Generated in the Service layer, in the same method that performs the mutation — the audit
write happens through `AuditLogService` before the surrounding `@Transactional` method
returns. It must share the transaction because the mentor's requirement (M-32) treats "the
state changed" and "we have a record that it changed" as one atomic fact. If they were two
separate transactions, a crash between them leaves one of two bad outcomes: an unaudited
mutation (a blind spot in a system whose entire purpose is auditability), or an audit record
for a change that then rolled back and never actually happened (a false accusation). Wrapping
both in one transaction makes it all-or-nothing — the only outcome that keeps the audit log
trustworthy.

## Where do email notifications originate, and why must they be asynchronous?

Originating in the Service layer via a `NotificationService`, dispatched **after** the
primary transaction has committed, on a dedicated `ThreadPoolTaskExecutor` behind `@Async`.
Email delivery is I/O-bound against an external SMTP server with unpredictable latency (or
outright downtime); if it ran on the request thread, a slow mail server would make every
login, share, or credential save feel slow — or fail outright — for something that isn't
essential to the user's immediate action. Async decouples "the operation succeeded" (fast,
guaranteed once the DB commit returns) from "the notification was sent" (best-effort, can be
retried or dropped independently without affecting the primary result).

## Layer diagram

```
Client (Postman / React)
      │  HTTPS
      ▼
┌──────────────── Spring Security filter chain ────────────────┐
│  CORS filter → JwtAuthenticationFilter (validate, set         │
│  SecurityContext, check Redis denylist) → ...                 │
└─────────────────────────────────────────────────────────────┘
      │  authenticated request only
      ▼
DispatcherServlet
      │
      ▼
Controller        HTTP only: bind request DTO, call one Service method,
                   wrap the result in ApiResponse<T>. No entity in or out. (§9)
      │
      ▼
Service           All business logic. @Transactional where two tables are
                   touched. Orchestrates:
      │
      ├──► AesEncryptionService     encrypt/decrypt vault secrets (AES-256-GCM)
      ├──► AuditLogService          writes inside the same transaction
      ├──► Redis                    JWT denylist check · vault-list cache read/evict
      └──► @Async NotificationService   off the request thread → SMTP / MailHog
      │
      ▼
Repository        Spring Data JPA interfaces only. No logic. (§9)
      │
      ▼
Hibernate          Entity ↔ row mapping. ddl-auto=validate — schema is Flyway's job,
                   Hibernate only checks entities against what Flyway already applied.
      │
      ▼
PostgreSQL          Durable source of truth. Flyway-migrated (V<n>__*.sql, D-04).
```

## Why a modular monolith and not microservices

Master §3 (Track C) explicitly rules out microservice decomposition for this project, and the
reasoning holds up independently of that instruction: the mentor grades layering and
correctness — whether a Controller stays thin, whether a Service owns its transaction,
whether ownership is checked before every access — not whether the system survives a
zone outage or scales past one instance. SecureVault has no team boundary that would benefit
from independent deploys, no component with a load profile different enough from the rest to
need scaling separately, and zero budget for the orchestration, service discovery, and
distributed-transaction handling microservices would require in exchange.

Feature-first packages (`user/`, `vault/`, `password/`, `sharing/`, ...) give the same
*ownership boundaries* a microservice split would — one package, one team, one reviewable
unit of change — without paying for a network call where a method call would do. If this
project ever outgrew a monolith, these package boundaries are exactly where it would be cut
apart; nothing about this architecture makes that harder later, and nothing about it pretends
that need exists today.

---
_Session S0.2 — 2026-08-11._
