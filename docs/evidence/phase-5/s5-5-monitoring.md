# S5.5 — Security monitoring and anomaly detection: evidence

## Brute-force lockout
```
5x POST /auth/login {wrong password}    -> 401 x5
6th POST /auth/login {CORRECT password} -> 401, IDENTICAL body to a wrong-password 401
   {"message":"Invalid email or password","errorCode":"INVALID_CREDENTIALS"}

SELECT account_locked, failed_login_attempts FROM users WHERE email=...;
  -> t | 6

SELECT type, severity, message FROM security_alerts ... ORDER BY id;
  ELEVATED_FAILED_ATTEMPTS  MEDIUM  "3 failed login attempts within 15 minutes"
  ELEVATED_FAILED_ATTEMPTS  MEDIUM  "4 failed login attempts within 15 minutes"
  BRUTE_FORCE_LOCKOUT       HIGH    "Account locked after 5 failed login attempts within 15 minutes"
  BRUTE_FORCE_LOCKOUT       HIGH    "Account locked after 6 failed login attempts within 15 minutes"

SELECT successful, failure_reason FROM login_attempts WHERE email=... ORDER BY id;
  f BAD_CREDENTIALS  (x5)
  f ACCOUNT_LOCKED    (6th — the correct-password attempt, rejected pre-password-check)
```

## Excessive vault access (rule 3)
```
51x GET /api/vault/{id}  (same credential, rapid succession)
GET /api/monitoring/alerts ->
  [{"type":"EXCESSIVE_VAULT_ACCESS","severity":"MEDIUM",
    "message":"50 credential reads within 10 minutes — above baseline"}]
```
Exactly one alert, fired at the 50th read — not 51 duplicate alerts (SETNX guard confirmed).

## Mass permanent deletion (rule 4)
```
5x (create -> soft-delete -> permanent-delete)
GET /api/monitoring/alerts ->
  [{"type":"MASS_PERMANENT_DELETE","severity":"HIGH",
    "message":"5 permanent deletions within 10 minutes — above baseline"}]
```

## New device / new IP (rule 1)
```
login {User-Agent: BrowserA}  -> no alert (first-ever device is not an anomaly)
login {User-Agent: BrowserB}  -> exactly one NEW_DEVICE alert
```

## Monitoring endpoints + risk score
```
GET /api/monitoring/login-attempts -> own history, newest first
GET /api/monitoring/login-attempts?all=true {as ADMIN} -> cross-user data (24 rows vs. own ~3)
GET /api/monitoring/risk-score ->
  {"score":45,"contributingFactors":["3 failed login attempt(s) in the last 24h",
   "1 unresolved security alert(s)"]}
  -- 3 failures x 10 pts = 30, + 1 unresolved alert x 15 pts = 15, total 45 — matches the
  -- documented formula in MonitoringController exactly.
```

`mvn clean verify` green.
