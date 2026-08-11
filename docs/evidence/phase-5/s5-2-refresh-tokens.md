# S5.2 — Refresh tokens, logout, Redis denylist: evidence

## Rotation and reuse detection (post-fix, `noRollbackFor`)
```
login                         -> accessToken A1, refreshToken T1
POST /auth/refresh {T1}       -> 200, {A2, T2} (T1 now revoked)
POST /auth/refresh {T1} again -> 401 TOKEN_INVALID  (reuse of an already-rotated token)
POST /auth/refresh {T2}       -> 401 TOKEN_INVALID  (whole family revoked by the reuse above,
                                                      even though T2 itself was never replayed)
```
Before the fix: the second call above (replaying T2) returned 200 — the family revoke never
actually committed because it ran inside the same `@Transactional` method that then threw
`TokenInvalidException`, triggering Spring's default rollback. Confirmed fixed by re-running the
identical sequence after adding `noRollbackFor = TokenInvalidException.class`.

## Logout
```
login                              -> accessToken A, refreshToken R
GET /api/vault {A}                 -> 200
POST /auth/logout {R}              -> 200
GET /api/vault {A} (same token)    -> 401 (denylisted, immediately — no delay)
POST /auth/refresh {R}             -> 401 TOKEN_INVALID (revoked)
redis-cli KEYS 'jwt:denylist:*'    -> jwt:denylist:<jti>
redis-cli TTL jwt:denylist:<jti>   -> 894  (~15 min, matches remaining access-token lifetime)
```

## Fail-open Redis policy
```
docker stop securevault-redis
POST /auth/login                   -> 200, success:true (denylist check not on the login path)
GET /api/vault {fresh access token} -> 200  (denylist check failed open, not closed)
docker start securevault-redis
GET /api/vault {same token}        -> 200  (works normally once Redis is back)

app log: WARN TokenDenylistServiceImpl - Redis unavailable — treating token as not denylisted
         (fail-open, ADR-024): Redis command timed out
```

## JWT test matrix (unchanged from Phase 1, reconfirmed)
```
no token       -> 401
valid token    -> 200
malformed token -> 401
```

`mvn clean verify` green.
