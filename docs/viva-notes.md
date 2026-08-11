# SecureVault — Viva / Explain-Back Notes (W-6)

Written as if answering a mentor live, without reading from code. Sections 1-5: S4.8, end of
Phase 4. Sections 6-9: S5.8, end of Phase 5.

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

## 6. How JWT access tokens, refresh tokens, and the Redis denylist fit together

The access token is short-lived (15 min) and completely stateless — the server never looks it up
anywhere, it just verifies the signature and expiry. That's fast but has one gap: there's no way
to force-expire one early. The refresh token fills the "long session" role (7 days) but is opaque
and hashed in Postgres, so the server *can* look it up, rotate it, and revoke it — every refresh
call invalidates the old token and issues a new one in the same family, and if an old, already-
rotated token ever gets replayed, that's a strong signal of theft, so the whole family dies at
once, not just the replayed token. Logout closes both gaps at once: the refresh token gets revoked
in Postgres immediately, and the *current* access token's `jti` gets added to a Redis denylist
with a TTL equal to whatever time it had left — so it stops working immediately instead of quietly
remaining valid for up to 15 more minutes. I chose fail-open for the denylist check specifically:
if Redis is down, every authenticated request would otherwise 500, and I judged a Redis outage
taking down the whole API to be worse than the narrow, time-bounded risk of a just-logged-out
token still working for at most 15 minutes in that exact window.

## 7. Why the MFA challenge token is peeked, not consumed, until a code actually verifies

My first version deleted the Redis challenge token the moment it was looked up, regardless of
whether the code the user typed was right or wrong. That meant one mistyped digit permanently
killed the login attempt — the user had to go all the way back to entering their password again,
even though they still had a minute and a half left on the actual 2-minute window. I found this
live, by deliberately testing a wrong code followed by a correct one against the same token, and
seeing the correct one fail too. The fix separates "read the token to find out whose login this
is" from "this token has now been used" — the token is only actually deleted once a code
verifies. That's a small distinction but it's the difference between a usable retry flow and one
that punishes a single typo as harshly as a completely wrong password.

## 8. The transaction-boundary bug in `@TransactionalEventListener(AFTER_COMMIT)`

This is the one I'd walk a mentor through most carefully, because it's genuinely subtle. The
whole point of `AFTER_COMMIT` is "only run this after the triggering transaction has actually
committed" — for notifications, that matters because you never want to email someone about a
share that then rolled back for an unrelated reason. But when that callback runs, Spring hasn't
necessarily finished *cleaning up* the just-committed transaction's resources yet — the commit
itself has happened, but the thread can still be holding onto that transaction's now-defunct
persistence context for a moment longer. If the callback then calls another `@Transactional`
method with the default propagation, Spring sees what looks like an existing transaction on the
thread and tries to participate in it instead of starting a clean one — and that "existing"
transaction is already finished, so the write silently goes nowhere: no exception, no SQL insert,
just a `null` id where a real one should be. I found this because a notification row simply never
appeared even though the surrounding code all reported success. The fix is
`Propagation.REQUIRES_NEW` on the write, which forces a genuinely new transaction no matter what
state the thread was carrying. I'd flag this as a "know your framework's ordering guarantees, not
just its documented happy path" lesson.

## 9. Why caching and authorization checks can't live in the same method

`@Cacheable` works by wrapping a method call: on a cache hit, the method body never runs at
all — the cached value is returned directly by the proxy. I initially put the ADMIN role check
*inside* the same method I'd annotated `@Cacheable` for `/api/admin/stats`. That worked the first
time (a genuine admin call, cache miss, check runs, populates the cache) — but the very next call
within the 2-minute TTL, from a completely different, genuinely non-admin user, got back 200 with
real admin data, because the cache hit skipped the method body entirely, check included. I found
this by deliberately testing with two distinct accounts rather than trusting the first success. The
fix is architectural, not a config flag: the authorization check now lives in the controller
method, which is never cached and therefore always runs; the actual data computation moved to a
separate service method that *is* cached, and which contains no per-caller logic at all. The
general rule I'd give a mentor: anything that gates access must live outside anything that can be
served without re-running.
