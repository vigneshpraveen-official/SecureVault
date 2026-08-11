# S5.4 — MFA (TOTP) + device/session tracking: evidence

## Setup → verify → enabled
```
POST /auth/mfa/setup {no auth}          -> 401
POST /auth/mfa/setup {auth}             -> 200 {secret, otpauthUri, qrCodeDataUri}
  otpauthUri: otpauth://totp/user%40sv.test?secret=...&issuer=SecureVault&algorithm=SHA1&digits=6&period=30
  qrCodeDataUri: data:image/png;base64,iVBORw0KGgo...
POST /auth/mfa/verify {code: wrong}     -> 401 MFA_INVALID
POST /auth/mfa/verify {code: computed independently via RFC 6238 in Python}
  -> 200 {backupCodes: [10 codes, e.g. "SVQK-6UPH", ...]}
```

## Login-with-MFA round trip, retry semantics, replay guard
```
POST /auth/login {correct password}     -> 200 mfaRequired:true, mfaChallengeToken, no tokens
POST /auth/mfa/challenge {token, wrong code}  -> 401 MFA_INVALID
POST /auth/mfa/challenge {SAME token, correct code} -> 200, real accessToken/refreshToken
  (proves the wrong-code attempt did NOT burn the challenge token — the pre-fix bug)
POST /auth/mfa/challenge {SAME token again}   -> 401 (already consumed, single-use confirmed)

# replay guard
POST /auth/login -> new challenge; POST /auth/mfa/challenge {same TOTP code as above} -> 401
  (code already accepted once this window — rejected as a replay, not re-verified as valid)
```

## Backup codes
```
POST /auth/mfa/challenge {backup code}        -> 200, real tokens
POST /auth/mfa/challenge {same backup code again} -> 401 (single-use)
```

## Disable (requires a live code, not just a session)
```
POST /auth/mfa/disable {wrong code}     -> 401, MFA still enabled
POST /auth/mfa/disable {correct code, fresh time-step} -> 200
POST /auth/login (same user)            -> mfaRequired:false (MFA now off)
```

## Devices
```
GET /api/monitoring/devices             -> [{id, deviceName, ipAddress, userAgent, lastSeenAt, trusted}]
DELETE /api/monitoring/devices/{id}     -> 204
POST /auth/refresh {that device's refresh token} -> 401 TOKEN_INVALID (revoked)
DELETE /api/monitoring/devices/{id} {as a DIFFERENT user} -> 403
```

`mvn clean verify` green.
