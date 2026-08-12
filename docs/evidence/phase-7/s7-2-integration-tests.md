# S7.2 — Integration tests: evidence

`@SpringBootTest` (MOCK web environment, real `MockMvc`) + Testcontainers PostgreSQL 16 + Redis 7,
`spring-boot-testcontainers` + `@ServiceConnection` — real Postgres, not H2, per the prompt's
explicit instruction (H2 hides Postgres-specific behaviour and Flyway differences). Flyway
migrations run against the container on every test run, which doubles as migration validation.

## Journeys covered
| Test class | Journey |
|---|---|
| `VaultJourneyIntegrationTest` | register → login → create → list (no password field) → reveal (decrypted) → update password → reuse-rejected (409) → soft delete → trash → restore → permanent delete → 404 on repeat; separately, full cross-user 403 isolation across GET/PUT/DELETE |
| `VaultPaginationIntegrationTest` | 15 seeded credentials across 3 categories; page/size totals, empty-page-beyond-last (200 not error), sort asc/desc, unwhitelisted `sortBy` (400 not 500), category filter alone, title filter alone, combined category+title+sort+pagination |
| `SharingJourneyIntegrationTest` | owner grants READ → recipient reads → recipient's update/delete rejected (403) → owner upgrades to EDIT → recipient updates (200) → recipient still can't delete → owner revokes → recipient denied immediately; separately, self-share (400) and duplicate-share (409) rejection |
| `AuditRollbackIntegrationTest` | `app.testing.force-audit-failure=true` (test-only Spring property, own `@TestPropertySource` context) throws inside the same `@Transactional` boundary as the credential save → whole transaction rolls back → credential count and audit-log count both unchanged, no partial row survives |

## Two real bugs found and fixed while building this suite (neither is application code)
See `docs/evidence/security-matrix.md` "Finding 3" and `docs/decisions.md` ADR-036 for full detail:
1. Shared `static` Testcontainers fields annotated `@Container` were being stopped after the
   first test class finished, breaking every class that ran after it (500s on register/login).
   Fixed with Testcontainers' documented singleton-container pattern (manual `static` start,
   no `@Container`, never stopped).
2. `VaultPaginationIntegrationTest`'s `@BeforeEach` registered the same literal email across all
   7 of its test methods; once the container-lifecycle bug above was fixed, data correctly
   persisted across the whole class (no per-method rollback in this MockMvc setup) and the 2nd+
   method 409'd on the now-real duplicate. Fixed with a random-UUID email per test run.

## Run
```
mvn clean verify
...
Tests run: 90, Failures: 0, Errors: 0, Skipped: 0
[INFO] Spotless.Java is keeping 198 files clean
BUILD SUCCESS
```

## Docker availability
`@Testcontainers(disabledWithoutDocker = true)` on `AbstractIntegrationTest` — every integration
test SKIPS (not fails) if Docker isn't reachable from the JVM running Maven. Verified both states
live in this same session: Docker was briefly unreachable (host machine's Docker Desktop service
had stopped — `systemctl --user status docker-desktop` showed `inactive (dead)`), all 12
integration tests reported `Skipped: 1` each, `mvn test` still `BUILD SUCCESS`; after
`systemctl --user start docker-desktop` + `docker compose up -d`, the same suite ran for real —
90/90 green, per above.
