# S6.1 — Frontend scaffold: evidence

## Build
```
npm run build
✓ 1883 modules transformed.
dist/index.html                   0.45 kB
dist/assets/index-*.css          16.73 kB
dist/assets/index-*.js          330.59 kB
✓ built in 951ms
```
`oxlint src/` — clean, no warnings after removing an unused `rejectWithValue` param.

## React 18 pin confirmed (Vite defaults to 19)
```
npm ls react react-dom @vitejs/plugin-react
├── @vitejs/plugin-react@6.0.5
├── react-dom@18.3.1
└── react@18.3.1
```
Every dependency (Redux Toolkit, React Router, react-hot-toast, lucide-react) dedupes onto
the same 18.3.1 — no dual-version tree.

## CORS preflight — backend already allows the Vite dev origin
```
OPTIONS /api/auth/login  Origin: http://localhost:5173
HTTP/1.1 200
Access-Control-Allow-Origin: http://localhost:5173
Access-Control-Allow-Methods: GET,POST,PUT,DELETE,PATCH,OPTIONS
Access-Control-Allow-Credentials: true
```

## Login response shape matches `authApi.login`/`persistSession` exactly
```json
{
  "success": true,
  "data": {
    "mfaRequired": false, "mfaChallengeToken": null,
    "accessToken": "eyJ...", "refreshToken": "afYK...",
    "userId": 14, "fullName": "Seed User",
    "email": "seed.user@securevault.local", "role": "USER"
  }
}
```

## The two 401 shapes the interceptor has to distinguish
```
GET /api/vault, no Authorization header       -> 401 {"errorCode":"INVALID_CREDENTIALS", ...}
GET /api/vault, Authorization: Bearer garbage -> 401 {"errorCode":"INVALID_CREDENTIALS", ...}
```
Both identical — the backend's `AuthenticationEntryPoint` (S1.2/S2.3) has no separate
TOKEN_EXPIRED code for the access-token path (that code exists only on the refresh-token
endpoint). `client.js`'s interceptor therefore keys off "did this request carry a bearer
token at all," not the error code, documented inline in the file.

## Refresh contract confirmed
```
POST /api/auth/refresh {refreshToken} -> 200 {accessToken, refreshToken}  (rotated)
Replaying the SAME (now-rotated) refresh token -> 401 {"errorCode":"TOKEN_INVALID"}
```

No real browser available in this environment — verification is build success (no console
errors possible to surface from `vite build`/`oxlint`), the dev server serving the SPA shell,
and live API-contract matching against the running backend, as above.
