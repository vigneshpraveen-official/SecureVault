# S7.3 — Security test matrix: evidence

Full matrix, methodology, and all three findings (with root-cause analysis and fixes) live at
**`docs/evidence/security-matrix.md`** (top-level, per the session prompt's explicit required
path) and its companion raw capture **`docs/evidence/security-matrix-raw.txt`**. The reusable
script is **`scripts/security-matrix.sh`**.

Summary: 68 real, live assertions against the running backend
(`mvn spring-boot:run -Dspring-boot.run.profiles=local`) + real docker-compose Postgres/Redis —
every protected route without a token (34 routes → 401), public-endpoint sanity check, malformed/
tampered/truncated token (401), cross-user resource access (403), READ-share update/delete/reshare
denial (403), revoked share (403), expired share (403), every non-admin-on-admin-route (403) vs.
real admin (200), logged-out access token via the Redis denylist (401), rotated-refresh-token
reuse (401), no `passwordHash` in the register response, and no email-existence oracle in login
error messages. All 68 rows PASS after fixing two test-script bugs found along the way (both
documented, neither in application code).
