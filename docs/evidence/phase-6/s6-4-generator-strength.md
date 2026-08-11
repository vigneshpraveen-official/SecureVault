# S6.4 — Generator and strength UI: evidence

## Generator — custom config and the validation-error path
```
POST /api/password/generate {length:24, includeSymbols:false, excludeAmbiguous:true} ->
  {"password":"WpAYjmQQ7WYYhUVn6x2erbmt",
   "strength":{"score":4,"strength":"Strong","entropyBits":99.3,"feedback":["Add a special character"]}}

POST /api/password/generate {all classes false} -> 400 VALIDATION_FAILED
  errors: [{"field":"generateRequest","message":"at least one character class must be enabled"}]
```
`GeneratorPanel`'s error handler reads `error.errors?.[0]?.message` first, falling back to
`error.message` — confirmed the object-level ("generateRequest") message surfaces in the toast
instead of the generic "Validation failed" wrapper text.

## Vault health widget
```
GET /api/vault/health ->
  {"totalCredentials":51,"veryStrongCount":51,"reusedPasswordCount":0,
   "staleCredentialCount":0,"healthScore":100, ...all other bands 0}
```
Every field `PasswordHealthWidget.jsx` reads is present; confirmed this endpoint has no
`topItemsToFix` (unlike the dashboard's password-health endpoint, S6.6), so the widget
correctly stops at aggregate counts rather than fabricating a fix-list.

## Nested-modal bug — found and fixed live
Before the fix: opening the Generator from inside the open Credential form modal, then
pressing Escape once, closed **both** modals (both `Modal` instances' `document`-level
keydown listeners fired for the same keypress). Fixed with `components/modalStack.js` — each
`Modal` only reacts to Escape/Tab if it's the top of the stack. Also fixed a duplicate
`id="modal-title"` on `aria-labelledby` when two modals are open simultaneously, switched to
`useId()`.

`npm run build` / `oxlint` clean after the fix.
