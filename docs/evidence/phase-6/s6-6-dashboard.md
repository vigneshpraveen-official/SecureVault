# S6.6 — Dashboard and analytics: evidence

## All four dashboard endpoints, shapes confirmed against every component's field access
```
GET /api/dashboard/summary ->
  {"totalCredentials":51,"byCategory":{"ENTERTAINMENT":7,"SOCIAL":7,"PERSONAL":8,"OTHER":8,
   "BANKING":7,"WORK":7,"DEVELOPMENT":7},"favoritesCount":0,"sharedInCount":0,
   "sharedOutCount":0,"trashCount":0,"lastLogin":"2026-08-11T15:45:39Z"}
  — byCategory keys match CATEGORY_VARIANT/BAR_COLOR exactly (CategoryChart.jsx).

GET /api/dashboard/password-health ->
  {"healthScore":100, ...band counts..., "topItemsToFix":[
     {"credentialId":23,"title":"Seed Site 1","reason":"Strength score 5/5"}, ...5 items]}
  — the ONLY endpoint with topItemsToFix; PasswordHealthCard links each item to /vault.

GET /api/dashboard/recent-activity -> [
  {"action":"PERMANENT_DELETE","description":"Permanently deleted credential",...},
  {"action":"DELETE","description":"Moved credential to trash",...},
  {"action":"REVOKE","description":"Revoked access to credential share",...}]
  — real audit trail from earlier live sessions rendering as plain-language rows + relative time.

GET /api/dashboard/alerts -> []
```

## Chart approach — plain markup, not SVG/library (ADR-033)
`CategoryChart`/`StrengthChart` render sorted horizontal bar lists via styled `<div>`s; the one
genuine SVG in the dashboard is `ScoreDial` (a true arc, one data point, shared with S6.4's
vault-health widget). No charting library was added — zero new dependencies this phase.

`npm run build` / `oxlint` clean.
