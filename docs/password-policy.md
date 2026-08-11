# SecureVault — Password Strength, Generation & Health Policy

Exact algorithms behind `POST /api/password/strength` (S3.1/M-29), `POST /api/password/generate`
(S3.2/M-30), and `GET /api/vault/health` (S3.3). Written so the score is **reproducible and
explainable in review** — this document and `PasswordStrengthServiceImpl` must stay in lockstep;
if they ever disagree, the code is the bug.

---

## 1. Strength scoring (`PasswordStrengthServiceImpl`)

### Base score — the mentor's baseline formula, exactly

Start at 0. Award **+1** for each of:

| Check | Condition |
|---|---|
| Length | `password.length() > 12` |
| Uppercase | at least one `A-Z` |
| Lowercase | at least one `a-z` |
| Digit | at least one `0-9` |
| Special | at least one character that is neither a letter, digit, nor whitespace |

Base score range: 0-5.

### Length feedback bands (8 / 12 / 16) — separate from the length *score*

The length check above only ever awards one point, at the >12 threshold. The three thresholds
the mentor's prompt calls out (8, 12, 16) instead shape which **feedback message** is shown,
independent of scoring:

| Length | Feedback |
|---|---|
| < 8 | "Increase length to at least 8 characters" |
| 8-11 | "Increase length to 12+ characters" |
| 12-15 | "Increase length to 16+ characters for extra safety margin" |
| ≥ 16 | none |

A password can score its length point at 13 characters and still get a "16+" suggestion — that's
intentional: the score rewards meeting the mentor's threshold, the feedback pushes toward
industry best practice (16+), which is a stronger bar than the scoring formula alone demands.

### Penalties — applied after the base score, each costs 1 point

1. **Consecutive repeats** — any run of **3 or more** identical consecutive characters (`aaa`,
   `111`). Detected by scanning for the longest run of `s[i] == s[i-1]`.
2. **Sequential patterns** — any run of **4 or more** characters that is either:
   - an ascending or descending ASCII run (`abcd`, `4321`), or
   - a substring of a keyboard row (`qwertyuiop`, `asdfghjkl`, `zxcvbnm`, `1234567890`) or its
     reverse, matching `qwerty`-style patterns.

   **Why 4, not 3:** a 3-character digit run like the `123` in `Welcome123` is common in
   otherwise-reasonable passwords and would over-penalize the mentor's own worked example
   (`Welcome123` → score 3, no sequence penalty). A 4+ character run (`1234`, `abcd`) is
   unambiguously a keyboard/alphabet walk, not incidental.
3. **Dictionary hit** — the **entire password**, lowercased, exactly matches an entry in
   `classpath:/password/common-passwords.txt` (~250 well-known weak/common passwords, curated by
   hand — not a downloaded wordlist, per the prompt's explicit instruction to keep it small).
   **Whole-string match, not substring** — a password merely *containing* a common word (e.g. a
   passphrase with "welcome" as one segment) is not penalized; only reproducing the sequence
   *exactly* is. This keeps the check simple, deterministic, and free of false positives on
   longer passphrases.

Final score = `clamp(base − penalties, 0, 5)`.

### Score → label

| Score | Label |
|---|---|
| 0 | Very Weak |
| 1-2 | Weak |
| 3 | Medium |
| 4 | Strong |
| 5 | Very Strong |

### Entropy — true Shannon entropy, not a charset-pool estimate

`entropyBits` is the Shannon entropy of the **submitted password's own character-frequency
distribution**, scaled by length:

```
H(password) = -Σ p(c)·log2(p(c))     for each distinct character c, p(c) = count(c) / length
entropyBits = H(password) × length
```

This is deliberately the textbook Shannon entropy formula applied to the password's empirical
symbol distribution (order-0), not the common "charset-pool" shortcut (`length × log2(poolSize)`)
some strength meters use instead. The pool-size shortcut answers "how big is the theoretical
search space this password could have come from"; this document's formula answers "how much
information is actually in the characters this password contains" — a password that reuses the
same few characters scores lower entropy than one with the same length and character classes but
no repetition, which is the more honest signal for a per-submission analysis. Rounded to 1
decimal place in the response.

### Worked examples (also the unit test suite)

| Password | Score | Label | Notes |
|---|---|---|---|
| `password` | 0 | Very Weak | dictionary hit + missing upper/digit/special |
| `Welcome123` | 3 | Medium | base 3 (upper+lower+digit), no penalties — matches the mentor's worked example exactly |
| 20-char random mixed | 5 | Very Strong | all 5 base points, no penalties |
| `aaaaaaaa1A!` | penalized | — | repeat penalty fires (8-run of `a`) |
| `abcd1234` | penalized | — | sequence penalty fires (both `abcd` and `1234` are 4-runs) |

### What this endpoint never does

- Never logs the submitted password (not even at DEBUG).
- Never persists or audits it, except where S3.3 explicitly stores the resulting **score**
  (never the plaintext) on a `Credential` row.
- Deterministic: no randomness anywhere in `analyze(...)`.

---

## 2. Password generation (`PasswordGeneratorServiceImpl`, S3.2)

### Why "pick randomly from the full pool" is wrong

A naive approach — build one combined pool from every enabled character class, then pick `length`
characters uniformly at random from it — can legally produce a password satisfying none of the
"must include a symbol" style guarantees a caller asked for. With a 4-class pool and length 8,
the probability of the generated password containing zero symbols is non-trivial and grows as
length shrinks; it is not just a theoretical edge case at length 8 (the prompt's minimum).

### The guarantee algorithm actually used

1. Build one pool per **enabled** class (uppercase, lowercase, digits, symbols). If
   `excludeAmbiguous` is set, remove `l`, `I`, `1`, `O`, `0` from whichever pool(s) contain them.
2. Pick **exactly one** character from each enabled pool, via `SecureRandom` — this guarantees
   every enabled class appears at least once, which a random fill from the union pool cannot.
3. Fill the remaining `length − enabledClassCount` slots by picking uniformly from the **union**
   of all enabled pools, via `SecureRandom`.
4. **Fisher-Yates shuffle** the resulting character list, via `SecureRandom` — without this step,
   the guaranteed-class characters from step 2 would always land in the first few positions,
   which is itself a detectable, non-uniform pattern.
5. Join to a string.

`java.util.Random` never appears anywhere in this codebase — every random decision in generation
(pool picks, fill picks, shuffle) uses the same injected `java.security.SecureRandom` instance.
Grep confirmation is captured in `docs/progress.md`'s S3.2 session log entry.

### Validation

- `length`: 8-128 inclusive (`@Min(8) @Max(128)`).
- At least one character class must be enabled — enforced by a custom class-level
  `@AtLeastOneCharacterClass` Bean Validation constraint on `GenerateRequest`, so a violation
  produces the same `400 VALIDATION_FAILED` shape as every other validation failure, rather than
  a one-off exception type.

### Response

Returns the generated password **and** its own `PasswordStrengthResponse`, computed by reusing
`PasswordStrengthService.analyze(...)` — no separate scoring logic for generated vs. user-entered
passwords.

---

## 3. Vault health score (`GET /api/vault/health`, S3.3)

### Inputs, per authenticated user

- `totalCredentials` — count of the user's credentials.
- Strength band counts — tallied from each credential's **stored** `strengthScore` (computed at
  create/update time, never recomputed on read) via `PasswordStrengthService.labelForScore(...)`.
  A credential with a `null` score (should not occur for any row created after S3.3, but is
  physically possible for pre-Phase-3 data) is excluded from both the band tally and the average
  used below, not treated as zero.
- `reusedPasswordCount` — every credential's password is decrypted **in memory only**, hashed
  with SHA-256, and grouped by hash. Any credential whose hash appears more than once counts
  toward this total. Plaintext and hashes are local variables only — never logged, cached, or
  returned; they go out of scope (and are eligible for GC) the moment the response is built.
- `staleCredentialCount` — credentials whose `passwordChangedAt` is more than 90 days old.
  `passwordChangedAt` is a dedicated column (`V2__add_strength_and_password_changed_at.sql`),
  set at creation and updated **only** when the stored password actually changes (S1.4's
  decrypt-and-compare logic already isolates that exact moment) — deliberately not reused from
  `updated_at`, which also changes on an unrelated edit like renaming a credential and would
  otherwise silently reset the "password age" clock on every edit.

### Health score formula (0-100)

If `totalCredentials == 0`: `healthScore = 100` (nothing to be unhealthy about).

Otherwise:
```
averageScore        = mean(strengthScore) over credentials with a non-null score (0 if none scored)
strengthComponent    = (averageScore / 5.0) × 60      // 0-60 points: how strong, on average
reusedRatio          = reusedPasswordCount / totalCredentials
uniquenessComponent  = (1 − reusedRatio) × 25          // 0-25 points: how unique
staleRatio           = staleCredentialCount / totalCredentials
freshnessComponent   = (1 − staleRatio) × 15           // 0-15 points: how recently changed
healthScore          = round(strengthComponent + uniquenessComponent + freshnessComponent)
                        clamped to [0, 100]
```

Weights (60/25/15) reflect that *how strong each password is* matters most, *reuse* is the
second-biggest real-world risk (one leaked site compromises every reused credential), and
*staleness* alone is the weakest signal (an old but strong, unique password is still fine).

### S4.6 note

Per-credential strength is computed synchronously today (inline in `create`/`update`). Bulk
recomputation (e.g. an admin "rescan all vaults" action) does not exist yet; when it does, it
must run through the async executor introduced in S4.6, not block the request thread — see the
`TODO(S4.6)` comments at the strength-computation call sites in `CredentialServiceImpl`.
