# SecureVault — API Contract

Live index of every endpoint that actually exists in the codebase, grown one row at a time
as each session ships it. See `docs/securevault_master.md` §11 for the full target surface
and §9 for the response envelope contract. Never document an endpoint that isn't real yet.

| Method | Path | Auth | Request DTO | Response DTO | Status codes | Error codes |
|---|---|---|---|---|---|---|
| GET | `/actuator/health` | none | — | `{ "status": "UP" }` | 200 | — |

---
_Last updated: S0.1 — 2026-08-10._
