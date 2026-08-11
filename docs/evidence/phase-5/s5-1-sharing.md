# S5.1 — Credential sharing and permissions: evidence

All verified live against the running app + Postgres. Four users (owner, READ, EDIT, unrelated),
one credential.

## Business rules
```
POST /api/share (self-share)          -> 400 SELF_SHARE_NOT_ALLOWED
POST /api/share (owner -> READ user)  -> 201, ShareResponse
POST /api/share (owner -> EDIT user)  -> 201, ShareResponse
POST /api/share (duplicate)           -> 409 SHARE_ALREADY_EXISTS
POST /api/share (READ user attempts)  -> 403 ACCESS_DENIED ("Only the owner can share")
```

## master §12 authorisation matrix — every cell
```
Owner:       GET 200, PUT 200
READ user:   GET 200 (password visible), PUT 403, DELETE 403
EDIT user:   GET 200, PUT 200 (title actually changed), DELETE 403
Unrelated:   GET 403, PUT 403
```

## Revocation and expiry
```
GET /api/share/received (READ user)      -> 1 active share
GET /api/share/sent (owner)               -> 2 active shares
DELETE /api/share/{readShareId}           -> 204
GET /api/vault/{id} as revoked READ user  -> 403 immediately (no cache, no delay)
PUT /api/share/{editShareId} permission=READ -> 200
PUT /api/vault/{id} as now-READ user      -> 403 (was 200 moments earlier as EDIT)
POST /api/share with expiresAt=(past)     -> 201, expired:true in response
GET /api/vault/{id} with expired share    -> 403 (behaves exactly like no share)
```

## Soft-delete / permanent-delete interaction
```
DELETE /api/vault/{id}  (owner soft-deletes)
GET /api/share/received (EDIT user)       -> [] (deleted credential hidden)
GET /api/vault/{id}     (EDIT user)       -> 404 CREDENTIAL_NOT_FOUND
POST /api/share on the deleted credential -> 404 CREDENTIAL_NOT_FOUND (can't share deleted)
PUT restore -> DELETE (soft) -> DELETE /permanent -> 204

SELECT * FROM credential_shares WHERE credential_id = <id>;  -- 0 rows (cleaned up)
SELECT action, entity_type, performed_by, details FROM audit_logs
  WHERE entity_type IN ('CREDENTIAL_SHARE') OR (entity_type='CREDENTIAL' AND action='ACCESS');
-- SHARE (create), SHARE (permission change), REVOKE, ACCESS x2 (one per shared reader) — all present,
-- performed_by correctly reflects the accessor's id, not the owner's, for ACCESS rows.
```

`mvn clean verify` green after every change in this session.
