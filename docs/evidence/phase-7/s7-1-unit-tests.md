# S7.1 — Service unit tests: evidence

JUnit 5 + Mockito, no Spring context — `mvn test -Dtest=<class>` per class, then the whole suite.

## Classes covered (S7.1's own minimum list)
| Class | Test file | What it proves |
|---|---|---|
| `AesEncryptionService` | `AesEncryptionServiceTest` | round trip; two encryptions of the same plaintext differ; tampered ciphertext (`GCMParameterSpec` auth tag) fails to decrypt; wrong-length key fails fast at construction |
| `PasswordStrengthServiceImpl` | `PasswordStrengthServiceImplTest` | every scoring rule (length>12, upper/lower/digit/special) and every penalty (repeat run, sequential run, dictionary hit), boundary cases, determinism |
| `PasswordGeneratorServiceImpl` | `PasswordGeneratorServiceImplTest` | config compliance (length, enabled classes), 1000-run uniqueness, class-guarantee, exclude-ambiguous, single-class-only |
| `UserServiceImpl` | `UserServiceImplTest` | duplicate-email rejection, BCrypt hashing applied, no hash in the returned response, async welcome-email dispatch |
| `CredentialServiceImpl` | `CredentialServiceImplTest` | ownership enforcement (owner/shared/stranger), decrypt-and-compare re-encrypt-only-on-change, soft-delete/restore/permanent-delete semantics, health-score computation |
| `CredentialShareServiceImpl` | `CredentialShareServiceImplTest` | self-share rejection, duplicate-share rejection, owner-only share/revoke, permission update |
| `AccessEvaluatorImpl` | `AccessEvaluatorImplTest` | owner/EDIT/READ/NONE resolution, expired-share denial |
| `JwtService` | `JwtServiceTest` | expired-token throws `ExpiredJwtException` (real production behaviour — see `docs/evidence/security-matrix.md` Finding 2) |

## Run
```
mvn test
...
Tests run: 90, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```
(90 includes S7.2's integration tests, run in the same `mvn test` invocation — see
`s7-2-integration-tests.md` for the split.)

## Naming convention
Every test method follows `should_<expected>_when_<condition>`, one behaviour per test. No test
depends on another test's state — each Mockito test builds its own mocks in the test body or via
`@BeforeEach`, and each Testcontainers-backed test in S7.2 registers a fresh, randomly-suffixed
user rather than sharing fixture data across tests.
