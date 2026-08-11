# S6.2 — Auth screens: evidence

## Register — success, duplicate, validation
```
POST /api/auth/register {fullName, email, password: "Str0ng!Pass"} -> 201
  {"id":39,"fullName":"Frontend Test","email":"...","role":"USER","createdAt":...}
  (no passwordHash field — matches what RegisterPage never even has to strip)

POST /api/auth/register (same email again) -> 409 DUPLICATE_EMAIL
  "Email already registered: frontend.test@securevault.local"

POST /api/auth/register {fullName:"X", email:"bad-email", password:"weak"} -> 400 VALIDATION_FAILED
  errors: [
    {field:"password", message:"must be between 8 and 72 characters"},
    {field:"password", message:"must contain at least one uppercase letter, ..."},
    {field:"email", message:"must be a well-formed email address"}
  ]
```
`fieldErrorsFrom()` concatenates multiple messages per field (password has two here) rather
than overwriting — confirmed both messages would render under the one password `Input`.

## Login — wrong password gives the exact same message as a locked account
```
POST /api/auth/login {wrong password} -> 401
  {"message":"Invalid email or password","errorCode":"INVALID_CREDENTIALS"}
```
`LoginPage` renders `error.message` verbatim — never invents a different string for a locked
vs. wrong-password case, matching the backend's anti-enumeration design (P5.5).

## Password strength — live, feeds RegisterPage's debounced meter
```
POST /api/password/strength {"password":"Str0ng!Pass"} ->
  {"score":4,"strength":"Strong","entropyBits":36.1,"feedback":["Increase length to 12+ characters"]}
```

## MFA — full challenge/retry cycle on a real TOTP secret
```
POST /api/auth/mfa/setup -> {secret: "LAIHDUXIN6MMIGHK3HVOHFBIFGY7ZB72", ...}
POST /api/auth/mfa/verify {code: <real TOTP>} -> {"backupCodes": [10 codes]}

Next login -> {"mfaRequired":true, "mfaChallengeToken":"sMFE..."}

POST /api/auth/mfa/challenge {challengeToken, code:"000000"} -> 401 MFA_INVALID
POST /api/auth/mfa/challenge {SAME challengeToken, code:<real TOTP>} -> 200, real tokens
```
Confirms the wrong-code attempt did NOT burn the challenge token (peek/invalidate design,
ADR-026) — exactly the retry flow `MfaForm` in `LoginPage.jsx` depends on.

`mvn clean verify` unaffected (no backend changes this phase). `npm run build` / `oxlint` clean.
