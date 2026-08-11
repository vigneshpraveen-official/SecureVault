# S6.7 — Admin console and audit viewer: evidence

## 403/200 matrix, two genuinely distinct accounts (never cross-promoted)
```
# seed.user (never an admin)
GET /api/admin/stats -> 403 | GET /api/admin/users -> 403 | GET /api/admin/audit-logs -> 403

# admin.tester, promoted to ADMIN in DB, fresh login (real JWT, no role claim — resolved fresh
# from the DB every request, so this is a real distinct-account test, not a stale-token artifact)
GET /api/admin/stats -> 200 {"totalUsers":42,"activeSessions":36,"failedLogins24h":10,
  "unresolvedAlertsBySeverity":{"MEDIUM":7,"HIGH":3},"systemHealth":"UP"}
GET /api/admin/users?search=seed -> 200, paginated, exact match
GET /api/admin/audit-logs?action=CREATE -> 200, 80 total, all rows action:"CREATE"
GET /api/monitoring/login-attempts?all=true -> 200, 47 rows across multiple users (confirms
  real admin scope, not just the caller's own attempts)
```

## Lock/unlock — and a real backend bug found live (ADR-035)
```
PUT /api/admin/users/14/status {"locked":true} -> 200 accountLocked:true
psql: SELECT account_locked FROM users WHERE id=14  -> t     (confirmed, no login attempt yet)
GET /api/admin/users?search=seed.user -> accountLocked: true  (confirmed again via the API)

seed.user calls POST /api/auth/login -> 200, real tokens (!)
psql: SELECT account_locked, failed_login_attempts FROM users WHERE id=14 -> f | 0
```
Root cause: `CustomUserDetailsService`'s auto-unlock heuristic (P5.5) clears `accountLocked`
whenever the most recent *failed* attempt is stale or absent — a user with zero failures has,
by definition, no failure to be stale, so the very next login attempt after an admin lock
silently clears it. Confirmed reproducible (re-locked and repeated the sequence with identical
results). Not fixed this phase — backend logic from P5.5, out of scope for a frontend-only
phase. Documented in ADR-035 and flagged directly to the developer.

`mvn clean verify` unaffected (no backend code changed). `npm run build` / `oxlint` clean.
