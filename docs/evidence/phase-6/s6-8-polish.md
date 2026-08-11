# S6.8 — Frontend polish: evidence

## Build output — route-level code splitting confirmed
```
npm run build
dist/assets/DashboardPage-*.js   10.53 kB
dist/assets/VaultPage-*.js       14.18-15.29 kB
dist/assets/AdminPage-*.js       12.31-12.56 kB
dist/assets/SharingPage-*.js      5.90-6.82 kB
dist/assets/TrashPage-*.js        3.88 kB
dist/assets/Modal-*.js            2.64 kB
dist/assets/ScoreDial-*.js        1.32 kB
... (Login/Register NOT split — bundled into the main entry, by design)
```
Each lazy route ships its own chunk rather than one monolithic bundle. No `.map` files in
`dist/` (`find dist -name "*.map"` -> 0). No `console.log`/`console.debug` anywhere in `src/`
(`grep -rn "console\.\(log\|debug\)" src` -> no matches).

## Async-state gap found and fixed
Before: `VaultPage`'s list-fetch failure (network error, 500, etc.) rendered the exact same
"You have no credentials" empty state as a genuinely empty vault — `vaultSlice`'s
`fetchVaultList.rejected` set `state.error` but nothing read it. Fixed: a distinct error state
with a Retry button, plus a toast on the same rejection. Same class of gap fixed on
`DashboardPage` (toasts if any of the four parallel dashboard calls fails).

## Consistency audit — two duplicated patterns consolidated
- Icon-only row-action buttons: identical hand-copied `className` string across `VaultRow`
  (6 buttons), `SharingPage` (4 buttons across two tabs), `GeneratorPanel` (2 buttons) →
  one `IconButton` component.
- Tab-switcher markup: byte-for-byte duplicated between `SharingPage` and `AdminPage` →
  one `Tabs` component.

## Mobile nav gap found and fixed
Before: the primary nav was `hidden sm:flex` with no mobile alternative at all — below the
`sm:` breakpoint there was no way to navigate to Vault/Sharing/Admin. Fixed with a fixed
bottom tab bar (`sm:hidden`), chosen over a hamburger menu since it stays reachable with one
thumb without an extra tap.

Both servers confirmed healthy after every change in this session:
```
curl http://localhost:5173/          -> 200
curl http://localhost:8080/actuator/health -> {"status":"UP"}
```
`npm run build` / `oxlint` clean at every step.
