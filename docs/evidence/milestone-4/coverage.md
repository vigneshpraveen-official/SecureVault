# S7.5 — Coverage: status

**Honest status, not a number.** JaCoCo was attempted this session (`jacoco-maven-plugin` 0.8.13,
`prepare-agent` + `report`) and then **reverted** — it never resolved from Maven Central (every
attempt across ~70 minutes returned HTTP 429, including bare requests to the repository root,
while general internet connectivity — npm registry, GitHub, Google — worked throughout the same
window), so `backend/pom.xml` currently has **no JaCoCo plugin at all**, keeping `mvn clean
verify` genuinely green (confirmed offline, 90/90 tests, Spotless clean) rather than leaving an
unresolvable plugin declared in a build that's never actually been run with it present. Full
detail and the exact verification steps taken are in `docs/decisions.md` ADR-038.

**No numbers below are estimated, guessed, or backfilled from reasoning about the code** — that
would be exactly the kind of fabricated evidence this project's whole methodology (live curl,
real `psql` output, real `mvn test` runs) has deliberately avoided since Phase 1. This file will
be updated with the real `target/site/jacoco/index.html` numbers the next time `mvn clean verify`
can actually reach Maven Central.

## What we know without JaCoCo, from the test run itself
`mvn clean verify` — 90 tests, 0 failures, 0 errors, 0 skipped (with Docker reachable). Real
service classes with at least one dedicated unit test asserting real behaviour (not just "does
it run"), per `docs/evidence/phase-7/s7-1-unit-tests.md`:

| Class | Test file | Test count |
|---|---|---|
| `AesEncryptionService` | `AesEncryptionServiceTest` | 6 |
| `PasswordStrengthServiceImpl` | `PasswordStrengthServiceImplTest` | 15 |
| `PasswordGeneratorServiceImpl` | `PasswordGeneratorServiceImplTest` | 8 |
| `UserServiceImpl` | `UserServiceImplTest` | 6 |
| `CredentialServiceImpl` | `CredentialServiceImplTest` | 24 |
| `CredentialShareServiceImpl` | `CredentialShareServiceImplTest` | 11 |
| `AccessEvaluatorImpl` | `AccessEvaluatorImplTest` | 6 |
| `JwtService` | `JwtServiceTest` | 2 |

Plus 12 Testcontainers-backed integration tests (`docs/evidence/phase-7/s7-2-integration-tests.md`)
exercising the full HTTP stack — controller, security filter chain, service, repository,
Hibernate, real Postgres — for the vault CRUD/trash/restore/permanent-delete journey, pagination/
sort/filter, the full sharing permission matrix, and the audit-rollback guarantee. These add real
coverage to classes with no *direct* unit test (controllers, `AccessEvaluatorImpl` again via HTTP,
`CredentialMapper`, `AuditServiceImpl`, `RefreshTokenServiceImpl`, `TokenDenylistServiceImpl`,
`DeviceServiceImpl`, `LoginAttemptServiceImpl`) that a unit-test-only view would miss entirely.

## Known, named gap (not hidden inside an average)
No dedicated unit test exists for: `admin/*ServiceImpl`, `dashboard/DashboardServiceImpl`,
`monitoring/{DeviceServiceImpl,LoginAttemptServiceImpl,SecurityAlertServiceImpl}`,
`notification/{EmailServiceImpl,NotificationServiceImpl,PasswordExpiryCheckServiceImpl}`,
`security/{MfaServiceImpl,MfaChallengeServiceImpl,RefreshTokenServiceImpl,TokenDenylistServiceImpl}`,
`common/async/AsyncTaskServiceImpl`, `common/audit/AuditServiceImpl`. Several of these are
exercised indirectly through S7.2's integration tests and S7.3's live security matrix (login,
logout, refresh, MFA-adjacent 401/403 paths all run through `RefreshTokenServiceImpl`/
`TokenDenylistServiceImpl`/`DeviceServiceImpl`/`LoginAttemptServiceImpl` for real), but that's
not the same as a dedicated unit test asserting each service's own branches directly — a future
session should add those before widening the JaCoCo `check` gate to cover them (ADR-038).

## Next step (exact command)
```bash
curl -sI https://repo.maven.apache.org/maven2/   # confirm 200, not 429, before retrying
cd backend
# re-add the jacoco-maven-plugin block (prepare-agent + report) to pom.xml — see ADR-038
mvn clean verify                                  # confirm it succeeds before committing
open target/site/jacoco/index.html               # or: python3 -m http.server, then browse
```
Then: fill in this file's numbers for real, and add the `check` goal execution described in
ADR-038's Consequences section.
