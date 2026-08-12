# S7.5 — Final P-AUDIT (W-4): findings table

Run at Phase 7 close across the whole backend and frontend, per `docs/securevault_prompts.md`'s
W-4 checklist. Phase 7 itself added zero production code (test files, `pom.xml` test-scoped
dependencies, and one frontend `.env.test` only), so this pass is really re-confirming the
pre-existing codebase is still clean, not auditing new feature work.

| # | Check | Result |
|---|---|---|
| 1 | Controller accepting/returning an entity instead of a DTO | None found |
| 2 | Endpoint not wrapped in `ApiResponse` (excluding the justified bodyless 204 delete responses) | None found |
| 3 | Exception handled outside `GlobalExceptionHandler`, or swallowed | None found — `grep -rl "@ExceptionHandler"` returns only `GlobalExceptionHandler.java` |
| 4 | Missing Bean Validation on an inbound request DTO | None found — every `dto/*Request.java` record carries at least one of `@Valid`/`@NotNull`/`@NotBlank`/`@Size`/`@Email`/`@Min`/`@Max`/`@Pattern`/`@URL` |
| 5 | `System.out.println`, `printStackTrace`, `e.printStackTrace` | None found in `src/main` |
| 6 | Logging a password/token/secret/decrypted value | None found — the two closest matches (`"Login password verified..."`, `"Credential password changed..."`) log the *event name*, never a value |
| 7 | `java.util.Random` in security-adjacent code | None found — the only two matches are doc-comments in `PasswordGeneratorServiceImpl` and `BackupCodeGenerator` explicitly stating the rule, not actual usages |
| 8 | `@ManyToOne` without `FetchType.LAZY`, unjustified | None found — `grep -rn "FetchType.EAGER"` is empty across the whole entity set |
| 9 | Multi-table write without `@Transactional` | None found. Two services initially looked suspicious by grep (`.save()` present, no `@Transactional`) but are correct by design: `AuditServiceImpl.record()` deliberately has no transaction boundary of its own — it must run inside the *caller's* existing `@Transactional` method so an audit failure rolls back the business write with it (P4.1); `MfaChallengeServiceImpl` only touches Redis (`StringRedisTemplate`), which `@Transactional` (a JPA/JDBC concern) doesn't apply to at all. |
| 10 | Package placement violations (feature-first layout) | None found — every controller lives under its own feature package (`admin/`, `dashboard/`, `monitoring/`, `notification/`, `password/`, `security/`, `sharing/`, `user/`, `vault/`) |
| 11 | Naming violations (entity/table/DTO/service conventions) | None found — unchanged from prior audits, no new classes added this phase besides test classes (`*Test.java`, following `should_<expected>_when_<condition>` method naming per S7.1's own explicit rule) |
| 12 | Schema change applied without a Flyway migration | N/A — Phase 7 made zero schema changes |
| 13 | Hardcoded secrets, URLs, or magic numbers that belong in configuration | One pre-existing, harmless match: `DevDataSeeder.java`'s `https://example{n}.test` fixture URL for dev-profile-only seed data (not a real endpoint, not new this phase) |
| 14 | Dead code, unused imports, commented-out blocks | Spotless (`removeUnusedImports`) runs on every build and is clean (`mvn clean verify` → `Spotless.Java is keeping 198 files clean`) |
| 15 | Endpoints missing from `docs/api-contract.md` | N/A — Phase 7 added zero new endpoints |

**Frontend-specific spot checks:**
- `console.log`/`console.debug` in `src/` (excluding test files): none found.
- Secret-pattern scan (`api[_-]?key`, `secret`, `password\s*=\s*['"]`, `private[_-]?key`,
  `-----BEGIN`) across new S7.4 test files: two matches, both the fake fixture string
  `'ghSecret1!'` used as a mock API response in `VaultPage.test.jsx` — not a real credential.
- `.env.test` (new, committed) correctly NOT matched by `.gitignore`'s `*.local` pattern
  (`git check-ignore -v frontend/.env.test` → not ignored, as intended — it has no secrets).

**Conclusion:** zero HIGH or MEDIUM findings. No fixes required by this pass — Phase 7's actual
bugs-found-and-fixed (Testcontainers container lifecycle, RTL cleanup, MSW URL matching,
concurrent `userEvent.type()`) were all in the session's own new test code, caught and fixed
live while writing it (see `s7-2-integration-tests.md`, `s7-4-frontend-tests.md`,
`docs/evidence/security-matrix.md`), not by this closing audit pass.
