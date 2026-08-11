# S5.6 — Notifications and async email: evidence

## Bug found live: AFTER_COMMIT listener needed REQUIRES_NEW
First attempt: `NotificationServiceImpl.create()` (plain `@Transactional`) returned normally,
`saved.getId()` was `null`, and no `insert into notifications` ever appeared in the SQL log —
yet the email step right after it logged "Email sent" successfully. Root cause: the AFTER_COMMIT
callback can still be running while the just-finished transaction's resources are thread-bound,
so the default `REQUIRED` propagation silently joined a transaction that was already done rather
than starting a new one. Fixed with `@Transactional(propagation = Propagation.REQUIRES_NEW)`.

## All five triggers, post-fix — notification row + real email, per trigger
```
POST /api/share {credential}                -> Notification(CREDENTIAL_SHARED) + email in MailHog
DELETE /api/share/{id}                      -> Notification(SHARE_REVOKED) + email
login from a new device                      -> Notification(NEW_DEVICE_LOGIN) + email
brute-force lockout (5 failed logins)         -> Notification(SECURITY_ALERT) + email
password-expiry sweep (credential backdated
  100 days, fast-cron override for testing)   -> Notification(PASSWORD_EXPIRY) + email
```

Verified via `GET /api/notifications` (row present, correct type/title/message) AND
`curl http://localhost:8025/api/v2/messages` (MailHog — real SMTP delivery, correct To/Subject)
for every trigger above.

## Read / read-all
```
PUT /api/notifications/{id}/read   -> 200, that row's read=true, others unchanged
PUT /api/notifications/read-all    -> 200, all rows read=true
```

## Rate-limited re-notification (password expiry)
```
1st sweep (2 users with stale credentials) -> notified=2
next 5 sweeps (same 2 users, no new stale credentials) -> notified=0 every time
  (Redis SETNX guard, 7-day window — confirmed via app log
   "Password expiry check complete: usersWithStaleCredentials=2, notified=0")
3rd distinct user's credential backdated mid-run -> next sweep notified=1 (only the new one)
```

`mvn clean verify` green after the fix.
