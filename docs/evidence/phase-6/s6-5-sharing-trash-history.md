# S6.5 — Sharing, trash, history UI: evidence

## Full sharing matrix — two genuinely distinct accounts (A = owner, B = recipient)
```
POST /api/share {credentialId, sharedWithEmail: B, permission: READ} -> 201
GET /api/share/received (as B) -> [{ ...permission:"READ", expired:false }]
PUT /api/vault/{id} (as B, READ) -> 403                       (Edit button correctly hidden)

GET /api/share/sent (as A) -> [{ id: shareId, ... }]
PUT /api/share/{shareId} {"permission":"EDIT"} (as A) -> 200
PUT /api/vault/{id} {"title":"Edited by B"} (as B, now EDIT) -> 200

DELETE /api/share/{shareId} (as A, revoke) -> 204
GET /api/vault/{id} (as B) -> 403                              (immediate, no stale access)
```

## Trash
```
DELETE /api/vault/93 -> 204
GET /api/vault/trash -> [{ "id":93, "title":"Edited by B", "updatedAt": <deletion instant> }]
  — CredentialSummaryResponse has no deletedAt field; updatedAt IS the deletion timestamp in
    practice since a soft delete sets both in the same write. Trash view labels it "Deleted".
DELETE /api/vault/93/permanent -> 204
```

## Edit-a-shared-credential fix (found during this session, before it shipped)
First draft passed the raw `ShareResponse` (title/owner/permission only) as the edit form's
`initialValues` — since `CredentialFormModal` sends every field on submit, this would have
silently blanked `username`/`websiteUrl`/`notes`/`category` on save (they're not `null` in the
payload, they're empty strings, which the backend's null-means-unchanged contract treats as
real values to set). Fixed by fetching the full `CredentialDetailResponse` first via
`vaultApi.getById`, and immediately discarding its decrypted `password` field before storing
anything in component state (destructured out, never held longer than the one line that reads
it) — the edit form never needs or displays it.

`npm run build` / `oxlint` clean.
