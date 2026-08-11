# S5.7 — Analytics dashboard APIs: evidence

## Dashboard summary — a real 3-category vault
```
GET /api/dashboard/summary ->
{
  "totalCredentials": 3, "byCategory": {"WORK":1,"SOCIAL":1,"BANKING":1},
  "favoritesCount": 0, "sharedInCount": 0, "sharedOutCount": 0, "trashCount": 0,
  "lastLogin": "2026-08-11T13:14:57.265362Z"
}
```

## Password health — ranked top-5-to-fix
```
GET /api/dashboard/password-health ->
{
  "healthScore": 76, "weakCount":1, "mediumCount":1, "veryStrongCount":1,
  "topItemsToFix": [
    {"credentialId":89,"title":"Bank1","reason":"Strength score 1/5"},
    {"credentialId":91,"title":"Work1","reason":"Strength score 3/5"},
    {"credentialId":90,"title":"Social1","reason":"Strength score 5/5"}
  ]
}
```
Weakest-first ordering confirmed correct against the actual passwords used (a 4-char password, a
10-char mixed password, a 20-char high-entropy password).

## Recent activity — human-readable
```
GET /api/dashboard/recent-activity ->
[{"action":"CREATE","entityType":"CREDENTIAL","description":"Created credential", ...}, ...]
```

## Admin stats — 403/200 + cache-bypass fix
```
GET /api/admin/stats {non-admin}        -> 403 ACCESS_DENIED
UPDATE users SET role='ADMIN' ...
GET /api/admin/stats {now-admin login}  -> 200
  {"totalUsers":35,"activeSessions":18,"failedLogins24h":9,
   "unresolvedAlertsBySeverity":{"MEDIUM":7,"HIGH":3},"systemHealth":"UP"}
GET /api/monitoring/login-attempts?all=true {admin} -> 24 rows (cross-user, confirms real admin scope)

# the bug: caching the WHOLE stats() method meant the check inside it never re-ran on a hit
GET /api/admin/stats {a DIFFERENT, genuinely non-admin user, cache still warm from the admin call above}
  -> 403 ACCESS_DENIED   (confirms the fix: check moved to the uncached controller method,
                           computation moved to a separate cached service bean)
```

`mvn clean verify` green.
