# SecureVault — Viva / Explain-Back Notes (W-6)

Written as if answering a mentor live, without reading from code. Session: S4.8, end of Phase 4.

## 1. Why BCrypt for account passwords but AES for vault secrets — what breaks if swapped

Account passwords are never needed back in plaintext — login only needs to verify a match, so
BCrypt (one-way, salted, deliberately slow) is right: even if the hash leaks, cracking it is
expensive. Vault secrets are different — the whole point of a password manager is handing the
user back their actual saved password, so it must be reversible. That's AES-256-GCM: two-way,
fast, and authenticated (GCM detects tampering). If I swapped them: hashing vault secrets with
BCrypt would make every saved password permanently unreadable — the core feature breaks
outright. Encrypting account passwords with AES instead of hashing them would mean anyone who
steals the database *and* the AES key gets every plaintext password back — far worse than a
slow, one-way hash that isn't reversible even with the key, because there is no key.

## 2. How a request travels from Postman to PostgreSQL and back

Postman sends HTTP to Spring's embedded Tomcat. `CorrelationIdFilter` tags it with an id first,
then `JwtAuthenticationFilter` validates the bearer token and populates the security context — no
token or a bad one means an `AuthenticationEntryPoint` bean writes a 401 right there and the
request never reaches a controller. If it passes, `DispatcherServlet` routes it to the matching
`@RestController` method, which validates the request body (`@Valid`) and calls a `@Service`.
The service holds all business logic — ownership checks, encryption, audit writes, all inside
one `@Transactional` boundary — and calls a Spring Data `Repository`, which Hibernate turns into
SQL against PostgreSQL. The entity comes back up, a MapStruct mapper turns it into a response
DTO (never the entity itself), and the controller wraps it in the `ApiResponse` envelope.

## 3. What the JWT filter does, where it sits, and why stateless

`JwtAuthenticationFilter` is a `OncePerRequestFilter` registered before Spring Security's own
`UsernamePasswordAuthenticationFilter` in the chain. On every request it reads the
`Authorization: Bearer` header, verifies the token's signature and expiry, and — if valid —
loads the user and populates `SecurityContextHolder` for the rest of that request only; nothing
is stored server-side. That's "stateless": the server doesn't keep a session table or in-memory
login state anywhere, so any instance can validate any request with just the token and the
shared signing secret. It's what lets the API scale horizontally without sticky sessions or a
shared session store, at the cost of needing a denylist (Redis, Phase 5) to revoke a token
early, since the server can't just "forget" a session the way it could with one.

## 4. Why the DTO layer exists at all

Four reasons, and I'd give the mentor all four: (1) API contract stability — the database schema
can change shape without breaking every client overnight, because the DTO is the actual public
contract, not the entity. (2) Over-posting prevention — a request DTO simply has no field for
`id`, `role`, or `deleted`, so a client can't smuggle in a value it shouldn't control by adding
an extra JSON key. (3) Avoiding lazy-loading failures — `Credential.user` is `LAZY`; serializing
the entity directly outside a transaction throws or N+1s, but the DTO never touches that
relation. (4) Never leaking internal fields — `passwordHash`/`encryptedPassword` physically
cannot appear in a type that has no field for them, which is a stronger guarantee than "we
remembered to exclude it this time."

## 5. Why the audit write is synchronous but the "activity log" is async (Phase 4's core design tension)

Both look like logging, but they mean different things. `AuditService.record(...)` is a
compliance record — if I created a credential but its audit row silently failed to write, that's
a gap a security reviewer must never find. So it runs as a plain synchronous call inside the
*same* `@Transactional` method as the credential save — if the audit write throws, the whole
transaction rolls back together, proven live by a test-only flag that forces the audit write to
fail and confirming both the credential and audit tables end up unchanged. `AsyncTaskService`'s
activity logging is different — it's informational, best-effort, and has nothing to roll back
with, since by the time it runs the business operation (e.g. login) has already succeeded. Moving
it off the request thread costs nothing and keeps the response fast; moving the audit write off
the thread would have silently broken the one guarantee that actually mattered.
