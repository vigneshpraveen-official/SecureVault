# SecureVault — Roadmap & Milestone Tracker

Full phase/session checklist, sourced from `docs/securevault_master.md` §16 and cross-referenced
against the mentor task stream in §5. This is the single place to see **total** project
progress at a glance — `docs/progress.md` stays focused on *current* state and the append-only
session log; this file is the static map both `progress.md` and every AI agent point back to.

**Tick a session's box the same session it closes** (part of Session Close / W-2 going
forward — see `docs/ai/CONTEXT.md`). Never mark a box done without a matching entry in
`docs/progress.md`'s SESSION LOG.

**Progress: 9 / 53 sessions complete. Milestone 1 complete.**

---

## Milestone 1 (Weeks 1–2) — Phases 0–1

### Phase 0 — Workspace & foundations
- [x] **S0.1** — Repo, docs system, Docker services, Spring Boot skeleton — *2026-08-10*
  Acceptance: `mvn spring-boot:run` starts; `/actuator/health` UP; Postgres + Redis containers up; all doc files exist.
- [x] **S0.2** — Product decomposition + architecture reasoning writeups (M-01, M-02) — *2026-08-11*
  Acceptance: `docs/decomposition.md` (Feature/Why table, ≥25 features) and `docs/architecture.md` (layer diagram + where JWT/AES/Redis/audit/email sit, with reasoning).
- [x] **S0.3** — Schema design, ERD, Flyway baseline (M-03, M-04, M-05) — *2026-08-11*
  Acceptance: DB `securevault` exists; `docs/db-design.md` complete with index rationale; ERD exported to `docs/erd/`; `V1__init.sql` applies cleanly.
  Note: ERD PNG export is a manual step at dbdiagram.io (see `docs/db-design.md`) — DBML source is committed, image not yet exported.

### Phase 1 — Milestone 1: auth + vault core
- [x] **S1.1** — User entity + registration + BCrypt (M-06, M-07) — *2026-08-11*
  Acceptance: Postman creates a user; row visible in `users`; duplicate email → 409; two identical passwords → different hashes (screenshot).
- [x] **S1.2** — Spring Security + JWT + login (M-15..M-19) — *2026-08-11*
  Acceptance: Login returns JWT; `/api/vault/**` without token → 401; with token → 200; expired token → 401.
- [x] **S1.3** — Credential entity + AES-GCM + create/read (M-08, M-09, M-10) — *2026-08-11*
  Acceptance: DB column holds ciphertext only; GET returns the original password; multiple credentials per user.
- [x] **S1.4** — Update, delete, ownership verification (M-11, M-12, M-13) — *2026-08-11*
  Acceptance: Password re-encrypted only when changed; deleting one row leaves others intact; other user's credential → 403.
- [x] **S1.5** — Category enum, search, filter, indexes (M-20..M-23) — *2026-08-11*
  Acceptance: Partial-title search works; category filter works; empty result → empty list not error; index rationale written.
- [x] **S1.6** — Milestone 1 evidence pack (M-14) — *2026-08-11*
  Acceptance: Every checklist line ticked with a screenshot or Postman run in `docs/evidence/milestone-1/`.

---

## Milestone 2 (Weeks 3–4) — Phases 2–4

### Phase 2 — Production-grade API refactor
- [ ] **S2.1** — DTO layer + MapStruct mappers (M-24, M-28)
- [ ] **S2.2** — Bean Validation across all requests (M-25)
- [ ] **S2.3** — Custom exceptions + `@ControllerAdvice` + `ApiResponse` (M-26, M-27)
- [ ] **S2.4** — Sweep + Postman regression

### Phase 3 — Password intelligence
- [ ] **S3.1** — Strength analyzer (M-29)
- [ ] **S3.2** — Generator with `SecureRandom` (M-30)
- [ ] **S3.3** — Entropy + vault integration

### Phase 4 — Data integrity, performance, operations
- [ ] **S4.1** — `@Transactional` + AuditLog with rollback proof (M-31, M-32)
- [ ] **S4.2** — Password history + reuse prevention (M-35, M-36)
- [ ] **S4.3** — Soft delete, restore, trash, permanent delete (M-37, M-38, M-39)
- [ ] **S4.4** — N+1 elimination (M-33)
- [ ] **S4.5** — Pagination, sorting, dynamic filtering + 50-row seed (M-34)
- [ ] **S4.6** — Async thread pool + background tasks (M-40, M-41)
- [ ] **S4.7** — SLF4J + logback-spring.xml (M-46, M-47)
- [ ] **S4.8** — Milestone 2 evidence pack

---

## Milestone 3 (Weeks 5–6) — Phases 5–6

### Phase 5 — Sharing, sessions, platform hardening
- [ ] **S5.1** — Credential sharing + permission model (M-42..M-45)
- [ ] **S5.2** — Refresh tokens, logout, Redis denylist
- [ ] **S5.3** — Redis caching + invalidation
- [ ] **S5.4** — MFA (TOTP) + device/session tracking
- [ ] **S5.5** — Security monitoring & anomaly detection
- [ ] **S5.6** — Notifications + async email
- [ ] **S5.7** — Analytics dashboard APIs
- [ ] **S5.8** — OpenAPI/Swagger + admin endpoints

### Phase 6 — React frontend
*(master §16 maps Phase 6 to both M3 and M4 — frontend work is expected to span the boundary.)*
- [ ] **S6.1** — Vite scaffold, Tailwind, router, axios interceptors, Redux store
- [ ] **S6.2** — Auth screens + protected routes + MFA
- [ ] **S6.3** — Vault UI — list, search, filter, pagination, CRUD, reveal/copy
- [ ] **S6.4** — Generator + live strength meter
- [ ] **S6.5** — Sharing UI + trash/restore
- [ ] **S6.6** — Dashboard & analytics
- [ ] **S6.7** — Admin console + audit log viewer
- [ ] **S6.8** — Polish

---

## Milestone 4 (Weeks 7–8) — Phases 6–9

### Phase 7 — Testing & quality
- [ ] **S7.1** — Service unit tests (Mockito) — encryption, strength, generator, sharing rules
- [ ] **S7.2** — Integration tests (`@SpringBootTest` + Testcontainers PostgreSQL) — auth and vault journeys
- [ ] **S7.3** — Security test matrix — 401/403 for every protected route, ownership and share cases
- [ ] **S7.4** — Frontend tests (React Testing Library) — auth form, vault list, strength meter
- [ ] **S7.5** — JaCoCo coverage report + gap closure on service layer

### Phase 8 — Deployment
- [ ] **S8.1** — Multi-stage Dockerfiles (backend + frontend) and full `docker-compose`
- [ ] **S8.2** — Neon Postgres + Upstash Redis provisioned; `prod` profile; migrations applied remotely
- [ ] **S8.3** — Render deploy — backend Docker web service (`PORT` binding, health check) + frontend static site; CORS wired
- [ ] **S8.4** — GitHub Actions CI — build, test, (optional) image publish
- [ ] **S8.5** — Reports & export module — PDF (OpenPDF) + Excel (Apache POI)
- [ ] **S8.6** — Final documentation — `guide.md` complete, architecture diagram, demo script, presentation deck

### Phase 9 — Submission & demo
- [ ] **S9.1** — Central-repo sync: branch, README/requirements removal, `clean verify`, push, notify mentor
  *(Blocked on mentor giving push/branch instructions — see `docs/decisions.md` ADR-006.)*
- [ ] **S9.2** — Demo rehearsal + evidence pack: full user journey in under 8 minutes, fallback screenshots for cold-start delays

---
_Total: 53 sessions across 10 phases. Last updated: S0.1 — 2026-08-10._
