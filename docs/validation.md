# SecureVault — Bean Validation reference (P2.2/M-25)

Every inbound request DTO is validated with `jakarta.validation` (Hibernate Validator, via
`spring-boot-starter-validation`) and `@Valid` on the controller parameter. A failing request
never reaches the service layer — `GlobalExceptionHandler` turns it into `400 VALIDATION_FAILED`
with a per-field error list, before any business logic runs.

## `@NotBlank` vs `@NotNull` — the mentor's explicit question

- **`@NotNull`** only rejects `null`. An empty string `""` or a string of only spaces passes.
- **`@NotBlank`** rejects `null`, `""`, and whitespace-only strings — it implies `@NotNull` and
  adds the blank check.

SecureVault uses `@NotBlank` everywhere a required field is a `String` (`fullName`, `email`,
`password`, `title`), because a request body like `{"title": "   "}` is not meaningfully
different from an omitted title — both should fail the same way. `@NotNull` alone would let it
through, hand a blank string to the service, and let it get stored — a real bug, not a
theoretical one, since JSON never forces a client to send a non-empty string.

We never use bare `@NotNull` on a `String` field in this project — every required string field
gets `@NotBlank` instead, for exactly that reason. `@NotNull` alone is reserved for non-`String`
required fields (none exist yet in Phase 1-2; it stays in scope for enums/objects later).

## `@Size` vs `@Pattern` — the mentor's other explicit question

- **`@Size`** constrains *length* (`min`/`max`), nothing about *content*. `@Size(min = 8)` accepts
  `"aaaaaaaa"` just as happily as `"Str0ng!x"`.
- **`@Pattern`** constrains *content* via a regular expression, independent of length (a
  `@Pattern` alone doesn't bound how long the match is, unless the regex itself does).

They compose: `UserRegisterRequest.password` uses **both** — `@Size(min = 8, max = 72)` for
length (72 is BCrypt's hard limit; anything past byte 72 of the UTF-8 encoding is silently
ignored by BCrypt, so accepting a longer password would be a lie about what's actually checked)
and `@Pattern` for the four-class complexity rule (upper, lower, digit, special character) the
P2.2 prompt asks for. `@Size` alone couldn't express "must contain a digit"; `@Pattern` alone
(`.{8,72}`) could, but a length rule inside a regex is harder to read and harder to get an exact
error message out of than a dedicated `@Size`.

## Annotation-by-annotation, by DTO

### `UserRegisterRequest` (`user/dto/UserRegisterRequest.java`)
| Field | Annotations | Why |
|---|---|---|
| `fullName` | `@NotBlank`, `@Size(max = 100)` | required; matches `users.full_name VARCHAR(100)` |
| `email` | `@NotBlank`, `@Email`, `@Size(max = 150)` | required, RFC-shape check, matches `users.email VARCHAR(150)` |
| `password` | `@NotBlank`, `@Size(min = 8, max = 72)`, `@Pattern` | required, length + BCrypt's 72-byte limit, four-class complexity |

### `LoginRequest` (`security/dto/LoginRequest.java`)
| Field | Annotations | Why |
|---|---|---|
| `email` | `@NotBlank`, `@Email` | required, shape check |
| `password` | `@NotBlank` | required presence only — **deliberately no `@Pattern`/`@Size`**: a user whose account predates a later policy tightening must still be able to log in with their existing password. Complexity is enforced once, at registration, not re-checked on every login. |

### `CredentialCreateRequest` (`vault/dto/CredentialCreateRequest.java`)
| Field | Annotations | Why |
|---|---|---|
| `title` | `@NotBlank`, `@Size(max = 150)` | required, matches `credentials.title VARCHAR(150)` |
| `username` | `@Size(max = 150)` | optional, matches `credentials.username VARCHAR(150)` |
| `password` | `@NotBlank` | required presence only — **deliberately no `@Pattern`**: this is a secret for a third-party site the user does not control (their bank, their email provider); SecureVault has no authority to demand it meet a complexity policy |
| `websiteUrl` | `@URL`, `@Size(max = 255)` | optional; validated as a URL only when present, matches `credentials.website_url VARCHAR(255)` |
| `notes` | `@Size(max = 2000)` | optional, app-level cap on a `TEXT` column — no schema-level limit, but an unbounded client-supplied string is still worth bounding |
| `category` | none | enum; an invalid value fails JSON deserialization before validation even runs (`400`, handled by `GlobalExceptionHandler`'s `HttpMessageNotReadableException` handler, not this file) |

### `CredentialUpdateRequest` (`vault/dto/CredentialUpdateRequest.java`)
Every field is optional — `null` means "leave unchanged" (S1.4). None use `@NotBlank`/`@NotNull`
for that reason; `@Size`/`@URL` still apply **when a field is present**, since Bean Validation
constraints (other than `@NotNull`/`@NotBlank`) pass automatically on `null` and only run against
a real value.

| Field | Annotations | Why |
|---|---|---|
| `title` | `@Size(min = 1, max = 150)` | if present, may not be blank or exceed the column length — `min = 1` blocks `""` without blocking `null` |
| `username` | `@Size(max = 150)` | same length bound as create |
| `password` | none | re-encryption is decrypt-and-compare business logic (S1.4), not a validation concern; presence alone (via `null` check) drives it |
| `websiteUrl` | `@URL`, `@Size(max = 255)` | same as create |
| `notes` | `@Size(max = 2000)` | same as create |
| `category` | none | same as create |

## Verification

For every endpoint above, one deliberately invalid payload was sent and produced `400` with the
exact offending field(s) named in `errors[]`, not a generic message or a 500. Evidence in
`docs/evidence/milestone-2/`.
