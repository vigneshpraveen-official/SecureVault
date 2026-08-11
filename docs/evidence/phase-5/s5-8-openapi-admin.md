# S5.8 — OpenAPI documentation and admin module: evidence

## Swagger / OpenAPI
```
GET /swagger-ui/index.html -> 200

GET /v3/api-docs -> valid OpenAPI 3.1 JSON
  openapi: 3.1.0
  title: SecureVault API
  paths count: 39
  tags: ['Admin','Auth','Dashboard','Monitoring','Notifications','Password','Sharing','Vault']
  has bearerAuth scheme: True
  /api/admin/stats present: True
  /api/share present: True
  /api/auth/mfa/setup present: True
```

## Admin module — full 403/200 matrix, all four endpoints
```
# genuinely separate non-admin user (never promoted) — confirmed via DB: role='USER'
GET /api/admin/stats       -> 403
GET /api/admin/users       -> 403
PUT /api/admin/users/1/status -> 403
GET /api/admin/audit-logs  -> 403

# same user's account promoted to ADMIN in DB, fresh login
GET /api/admin/stats       -> 200, real aggregate numbers
GET /api/admin/users?search=admin8 -> 200, paginated, correct match
GET /api/admin/audit-logs?action=CREATE -> 200, filtered correctly (78 total, all CREATE)
```

## Lock / unlock via admin
```
PUT /api/admin/users/{id}/status {"locked":true}  -> 200, accountLocked:true
SELECT account_locked FROM users WHERE id=... ;   -> t
PUT /api/admin/users/{id}/status {"locked":false} -> 200, accountLocked:false
```

## Method security note
An earlier test that reused the SAME email for both a "non-admin" and "admin" token produced a
false negative (200 instead of 403) — root cause: JWTs carry no role claim, so promoting that
email to ADMIN mid-test retroactively upgraded the already-issued "non-admin" token too, since
authorities are resolved fresh from the DB on every request (`CustomUserDetailsService`). Not an
application bug — re-run with two genuinely distinct users (above) confirms `@PreAuthorize`
enforces correctly.

`mvn clean verify` green.
