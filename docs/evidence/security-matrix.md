# Security test matrix — S7.3

Live curl matrix run against the real Spring Boot backend (`mvn spring-boot:run -Dspring-boot.run.profiles=local`)
with real PostgreSQL/Redis (docker-compose), driven by `scripts/security-matrix.sh` (a copy of the
exact script run for this evidence — see repo root). Every row below is a real HTTP status from a
real request; nothing here is inferred or hand-typed. Full raw output: this file transcribes
`docs/evidence/security-matrix-raw.txt`, captured the same run.

Four fresh users were registered for this run (`user-a-*`, `user-b-*`, `user-c-*`,
`admin-*@sv-test.local`), the admin one promoted via direct SQL (`UPDATE users SET role='ADMIN'`)
then re-logged-in so its JWT reflects the new role. All 68 rows below are **PASS** — every gap
found while building this matrix was fixed before this table was captured (see "Findings" below).

## A — Every protected endpoint, no token → 401

| Route | Expected | Actual |
|---|---|---|
| GET /api/vault | 401 | 401 |
| GET /api/vault/1 | 401 | 401 |
| POST /api/vault | 401 | 401 |
| PUT /api/vault/1 | 401 | 401 |
| DELETE /api/vault/1 | 401 | 401 |
| GET /api/vault/trash | 401 | 401 |
| GET /api/vault/1/history | 401 | 401 |
| GET /api/vault/health | 401 | 401 |
| PUT /api/vault/1/restore | 401 | 401 |
| DELETE /api/vault/1/permanent | 401 | 401 |
| POST /api/vault/recompute-strength | 401 | 401 |
| POST /api/share | 401 | 401 |
| GET /api/share/received | 401 | 401 |
| GET /api/share/sent | 401 | 401 |
| PUT /api/share/1 | 401 | 401 |
| DELETE /api/share/1 | 401 | 401 |
| GET /api/dashboard/summary | 401 | 401 |
| GET /api/dashboard/password-health | 401 | 401 |
| GET /api/dashboard/recent-activity | 401 | 401 |
| GET /api/dashboard/alerts | 401 | 401 |
| GET /api/admin/stats | 401 | 401 |
| GET /api/admin/users | 401 | 401 |
| PUT /api/admin/users/1/status | 401 | 401 |
| GET /api/admin/audit-logs | 401 | 401 |
| GET /api/monitoring/login-attempts | 401 | 401 |
| GET /api/monitoring/alerts | 401 | 401 |
| GET /api/monitoring/risk-score | 401 | 401 |
| GET /api/monitoring/devices | 401 | 401 |
| DELETE /api/monitoring/devices/1 | 401 | 401 |
| GET /api/notifications | 401 | 401 |
| PUT /api/notifications/1/read | 401 | 401 |
| PUT /api/notifications/read-all | 401 | 401 |
| POST /api/auth/mfa/setup | 401 | 401 |
| POST /api/auth/mfa/disable | 401 | 401 |

## A2 — Public endpoints, no token → not 401 (sanity check the allowlist isn't over-broad)

| Route | Expected | Actual | Note |
|---|---|---|---|
| POST /api/password/strength | 200 | 200 | stateless utility, no vault/user data touched |
| POST /api/password/generate | 200 | 200 | same |
| GET /actuator/health | 200 | 200 | ops probe |
| POST /api/auth/logout, no body | 400 | 400 | see **Finding 1** below — deliberately `permitAll`, revokes by `refreshToken` in the body, not by the access token |

## B — Malformed / tampered access token → 401

| Case | Expected | Actual |
|---|---|---|
| Not a JWT at all (`GET /api/vault`) | 401 | 401 |
| Valid JWT + trailing garbage appended | 401 | 401 |
| Valid JWT, last character of the signature flipped | 401 | 401 |
| Valid JWT with the signature segment truncated off | 401 | 401 |

**Expired token** is proved separately, deterministically, in `JwtServiceTest` rather than a live
15-minute wait (`JWT_ACCESS_EXPIRY_MS=900000`) — see **Finding 2** below.

## C — Valid token, another user's resource → 403

Stranger C, zero relationship to A's credential:

| Action | Expected | Actual |
|---|---|---|
| GET A's credential | 403 | 403 |
| PUT A's credential | 403 | 403 |
| DELETE A's credential | 403 | 403 |
| GET A's credential history | 403 | 403 |

## D — READ-share user attempts update/delete/reshare → 403

B holds an active READ share on A's credential:

| Action | Expected | Actual |
|---|---|---|
| GET A's credential (in-scope for READ) | 200 | 200 |
| PUT A's credential | 403 | 403 |
| DELETE A's credential | 403 | 403 |
| POST /api/share (B reshares A's credential — B isn't the owner) | 403 | 403 |

## E — Revoked share → 403

| Action | Expected | Actual |
|---|---|---|
| A revokes B's share (DELETE /api/share/{id}) | 204 | 204 |
| B, immediately after, GET A's credential | 403 | 403 |

No stale-cache window — revocation takes effect on the very next request (S5.3 cache eviction confirmed).

## F — Expired share → 403, behaves exactly like no share

A fresh READ share's `expires_at` was set to one hour in the past via direct SQL (Bean Validation
has no `@Future` constraint on `expiresAt` at creation, so an already-past expiry is only reachable
this way — matches how a share organically expires over time).

| Action | Expected | Actual |
|---|---|---|
| B, GET A's credential through the expired share | 403 | 403 |
| B's `/api/share/received` list length | — | 0 (expired shares are excluded outright, not flagged — M-45) |

## G — Non-admin on every `/api/admin/**` route → 403; real admin → 200

| Route | Actor | Expected | Actual |
|---|---|---|---|
| GET /api/admin/stats | regular user A | 403 | 403 |
| GET /api/admin/users | regular user A | 403 | 403 |
| GET /api/admin/audit-logs | regular user A | 403 | 403 |
| PUT /api/admin/users/1/status | regular user A | 403 | 403 |
| GET /api/admin/stats | real ADMIN | 200 | 200 |
| GET /api/admin/users | real ADMIN | 200 | 200 |
| GET /api/admin/audit-logs | real ADMIN | 200 | 200 |

## H — Logged-out access token (Redis denylist) → 401

| Action | Expected | Actual |
|---|---|---|
| C, before logout, GET /api/vault | 200 | 200 |
| C logs out (`POST /api/auth/logout` with `Authorization` header + real `refreshToken` body) | 200 | 200 |
| C's now-logged-out access token, GET /api/vault | 401 | 401 |
| C's now-revoked refresh token, POST /api/auth/refresh | 401 | 401 |

## I — Reuse of a rotated refresh token → 401

| Action | Expected | Actual |
|---|---|---|
| First `/api/auth/refresh` call with a not-yet-used refresh token | 200 | 200 |
| Replay of the same (now-superseded) refresh token | 401 | 401 |

Rotation confirmed structurally too: the token returned by the refresh call is a different string
from the one submitted (both 43 base64 characters, values differ).

## J — No `passwordHash` leakage; no email-existence oracle

- `POST /api/auth/register` response body inspected for a `passwordHash` key: **absent** (response
  is `{id, fullName, email, role, createdAt}` only).
- Login with a **non-existent** email vs. login with a **real** email + **wrong** password: both
  return the identical body — `"message":"Invalid email or password"`, `"errorCode":"INVALID_CREDENTIALS"`
  — so a caller cannot distinguish "no such account" from "wrong password" (`InvalidCredentialsException`,
  never a per-field validation error, per master §9).

---

## Findings

**Finding 1 — test-script false alarm, not a backend bug.** The first draft of this matrix put
`POST /api/auth/logout` in the blanket "no token → 401" list and got 400 back, then tried logging
out with only an `Authorization` header and no body and got 400 again. Reading `AuthController`
and `SecurityConfig` together explained why: `/api/auth/logout` is **deliberately** `permitAll` —
it revokes by the `refreshToken` in the request body (a `@Valid @RequestBody LogoutRequest`), not
by the access token, specifically so a caller can still fully log out even with an already-expired
or missing access token. A missing/blank `refreshToken` in the body is correctly `400
VALIDATION_FAILED`, not 401. Fixed the script to send a real `refreshToken`; Section H above is the
corrected, real result. No application code changed.

**Finding 2 — expired-token proof moved into a unit test, not a live wait.** The real access-token
lifetime is 15 minutes (`JWT_ACCESS_EXPIRY_MS=900000`); waiting that long for one curl call isn't a
reasonable use of a live-verification pass. `JwtServiceTest` builds a second `JwtService` instance
with a **negative** expiry window (`-1000`ms) via the exact same constructor and
`generateAccessToken()` path production code uses, producing a token that's already expired the
instant it's minted — no reflection, no private access, fully deterministic. Along the way this
surfaced that `JwtService.isTokenExpired`'s own `.before(new Date())` comparison is effectively
unreachable for a genuinely expired token: jjwt's parser throws `ExpiredJwtException` *while
parsing the claims*, before that comparison ever runs. That's fine — it's exactly what
`JwtAuthenticationFilter` catches (`catch (JwtException | ...)`, `JwtService.java`/
`JwtAuthenticationFilter.java`) to leave the request unauthenticated, the same code path that
produces every 401 in Section B above. The unit test now asserts the real behaviour
(`ExpiredJwtException` thrown), not the originally-assumed boolean return.

**Finding 3 — not a security issue, a test-isolation bug in this phase's own new integration
tests.** Building this matrix's companion Testcontainers suite (P7.2) surfaced two harness bugs,
both fixed, neither in application code:
1. `AbstractIntegrationTest`'s shared Postgres/Redis containers were annotated `@Container`, which
   ties each container's start/stop to *its owning test class's* JUnit lifecycle. With a shared
   `static` field across four test classes, the first class to finish stopped the container out
   from under every class that ran after it (register/login calls started returning 500). Fixed by
   switching to Testcontainers' documented singleton-container pattern: start both containers once
   in a `static` initializer, no `@Container` annotation, never stop them (the Ryuk reaper cleans
   up at JVM exit).
2. Once that was fixed, `VaultPaginationIntegrationTest` then failed instead with `409
   DUPLICATE_EMAIL` — its `@BeforeEach` registered the same literal email
   (`pagination@example.com`) for every one of its 7 test methods, and the shared container
   correctly persists data across the whole class (no per-method transaction rollback in this
   MockMvc setup). Fixed with a random-UUID email per test run.

Full backend suite after both fixes: `mvn clean verify` → **90/90 tests green**, Spotless clean —
see `docs/progress.md` S7.3 entry.
