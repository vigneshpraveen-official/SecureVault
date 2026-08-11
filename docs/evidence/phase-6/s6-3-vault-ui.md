# S6.3 — Vault UI: evidence

## Full CRUD cycle against the real backend, matching every component's field access

```
POST /api/vault {title, username, password, websiteUrl, category:"WORK"} -> 201, id=92

GET /api/vault?page=0&size=5&sortBy=title&direction=asc&category=WORK&title=S6.3 ->
  { "content":[{...}], "currentPage":0, "pageSize":5, "totalElements":1, "totalPages":1,
    "first":true, "last":true, "hasNext":false }
  — every field Pagination.jsx reads (currentPage/pageSize/totalElements/totalPages) present.

GET /api/vault/92 (reveal) -> {"password":"Str0ng!Pass1", ...}   -- decrypted, matches useRevealPassword

PUT /api/vault/92 {"title":"S6.3 Test Cred Renamed"} -> 200, title updated

DELETE /api/vault/92 -> 204 (soft delete)
GET /api/vault?title=S6.3 -> totalElements: 0        (confirms it left the active list)

PUT /api/vault/92/restore -> 200
GET /api/vault?title=S6.3 -> totalElements: 1        (Undo toast's restore call verified)

DELETE /api/vault/92 -> 204
DELETE /api/vault/92/permanent -> 204                 (cleanup)
```

## Favorite — confirmed read-only is the correct call, not a shortcut
```java
// CredentialCreateRequest / CredentialUpdateRequest — no `favorite` field at all
// CredentialMapper.java:
@Mapping(target = "favorite", ignore = true)   // on BOTH toEntity and updateEntityFromRequest
```
No write path exists anywhere in the API for `favorite`. The vault list star renders the
(always-false) value read-only rather than wiring a toggle that would return 200 while
silently changing nothing. See ADR-034.

`mvn clean verify` unaffected. `npm run build` / `oxlint` clean.
