# N+1 review and fix — P4.4/M-33

## 1. Relationship mappings and fetch strategy, entity by entity

| Entity | Relationship | Fetch | Why |
|---|---|---|---|
| `Credential.user` | `@ManyToOne` → `User` | LAZY | Never needed when listing/reading a credential (the caller already knows who they are — it's `principal`); loading it eagerly on every credential query would be pure waste. |
| `PasswordHistory.credential` | `@ManyToOne` → `Credential` | LAZY | Only ever needed to resolve the FK on write (`.credential(credential)` when saving a history row); never read back through this side. |
| `Credential` → `PasswordHistory` | **no mapping at all** | — | Deliberately no `@OneToMany` back-reference (see `PasswordHistory.java`'s class comment). A `Credential` never carries its own history collection, so nothing can *accidentally* lazy-load it just by touching a `Credential` object — every access goes through `PasswordHistoryRepository` explicitly. This is itself a fetch-strategy decision: the safest fetch strategy for a collection that would tempt exactly the N+1 bug below is to not model it as a navigable JPA relationship at all. |
| `AuditLog` | none — `performedBy`/`entityId` are plain `Long` columns | — | No FK, no relationship, on purpose (ADR-017): an audit row must outlive the entity it describes (P4.3's permanent-delete requirement), and a future "list audit logs" endpoint can never N+1 against `users` by construction, because there's no relationship for Hibernate to lazily walk. |

No `EAGER` fetch appears anywhere in the schema. Every `@ManyToOne` above is `LAZY`.

## 2. The N+1 case: `GET /api/vault` with per-credential history counts

S4.2 added password history; showing "how many times has this password been changed" per row
in the vault list (`historyCount` on `CredentialSummaryResponse`) is a genuinely useful health
signal. The naive way to compute it — give `Credential` a `@OneToMany(mappedBy = "credential")
List<PasswordHistory> passwordHistories` back-reference and call
`credential.getPasswordHistories().size()` once per row after the list query — is exactly the
"vault list touching related data" N+1 shape this session is meant to catch.

### Before

Temporarily added the back-reference and the naive per-row `.size()` call, seeded one user with
5 credentials (3 of which had their password changed twice, building real `password_history`
rows), enabled `show-sql` (already on in `application-local.yml`), and called `GET /api/vault`.

First finding, before any query even ran: **it doesn't just N+1, it throws.** This project's
`spring.jpa.open-in-view: false` (locked since S0.1) closes the Hibernate session at the end of
the repository call — `listForUser` wasn't `@Transactional`, so touching the lazy collection
afterward failed outright:

```
org.hibernate.LazyInitializationException: failed to lazily initialize a collection of role:
com.securevault.vault.Credential.passwordHistories: could not initialize proxy - no Session
```

Full trace: `s4-4-lazyinit-exception.txt`. Wrapping `listForUser` in `@Transactional(readOnly =
true)` (still temporarily, purely to get a comparable query count) made it run, and produced
exactly the N+1 shape:

```
select ... from credentials c1_0 left join users u1_0 on u1_0.id=c1_0.user_id
  where u1_0.id=? and not(c1_0.deleted)                              -- 1 query, 5 rows back
select ... from password_history ph1_0 where ph1_0.credential_id=?   -- repeated 5 times,
select ... from password_history ph1_0 where ph1_0.credential_id=?   --   once per credential
select ... from password_history ph1_0 where ph1_0.credential_id=?   --   returned by the
select ... from password_history ph1_0 where ph1_0.credential_id=?   --   query above
select ... from password_history ph1_0 where ph1_0.credential_id=?
```

Full log excerpt: `s4-4-nplusone-before-queries.txt`. **6 queries for 5 credentials** — 1 + N,
and it scales linearly with however many credentials are on the page.

### The fix

`PasswordHistoryRepository.countByCredentialIds(List<Long>)` — one JPQL query with `GROUP BY`,
batching every credential id on the page in a single `IN (...)`:

```java
@Query("SELECT ph.credential.id, COUNT(ph) FROM PasswordHistory ph "
     + "WHERE ph.credential.id IN :credentialIds GROUP BY ph.credential.id")
List<Object[]> countByCredentialIds(@Param("credentialIds") List<Long> credentialIds);
```

`CredentialServiceImpl#toSummaryResponses` calls it once per list/search/trash request, builds a
`Map<Long, Long>`, and merges the count into each `CredentialSummaryResponse` via
`CredentialMapper`'s multi-source-param pattern (same shape already used for
`toDetailResponse`'s decrypted password). The temporary `@OneToMany` back-reference and the
`@Transactional(readOnly = true)` added only for measurement were both removed immediately after
capturing the numbers below — the shipped `Credential` entity still has no back-reference to
`PasswordHistory`.

### After

Same seeded data, same endpoint, fresh app run:

```
select ... from credentials c1_0 left join users u1_0 on u1_0.id=c1_0.user_id
  where u1_0.id=? and not(c1_0.deleted)                                    -- 1 query, 5 rows
select ph1_0.credential_id, count(ph1_0.id) from password_history ph1_0
  where ph1_0.credential_id in (?,?,?,?,?) group by ph1_0.credential_id    -- 1 query, all 5
```

Full log excerpt: `s4-4-nplusone-after-queries.txt`. **2 queries, flat regardless of how many
credentials are returned** — 50 credentials would still be 2 queries, not 51.

### Why this technique, not JOIN FETCH or @EntityGraph

| Endpoint | Queries before | Queries after | Technique | Why this technique |
|---|---|---|---|
| `GET /api/vault` (list/search/trash) | 1 + N (6 for N=5) | 2, flat | Batched `COUNT ... GROUP BY` aggregate query | Only a *count* is needed, not the history rows themselves — `JOIN FETCH` or `@EntityGraph` would pull every `PasswordHistory` entity into memory (and, for a one-to-many `JOIN FETCH`, multiply the credential rows in the result set, requiring in-memory de-duplication) just to call `.size()` on the result. An aggregate query is the correct tool for "a count per parent, across N parents" — it's the smallest amount of data that answers the actual question. |

**Never fixed by making the relationship `EAGER`.** `EAGER` would still be a per-credential
lazy-vs-eager join query pattern under the hood (Hibernate has no batch-eager-fetch-a-collection
mode without `@BatchSize`/`@Fetch(SUBSELECT)` — plain `EAGER` on a `@OneToMany` still N+1s), and
it would apply to *every* query that touches a `Credential`, including the single-credential
`GET /api/vault/{id}` and `POST`/`PUT`, which never need the history collection at all — trading
one endpoint's N+1 for a permanent, unconditional over-fetch on every other endpoint.

## 3. show-sql

`spring.jpa.show-sql: true` is `application-local.yml`-only (already was, since S0.1) — never
in `application.yml` (shared) or `application-prod.yml`. No change needed this session; already
correctly scoped to local development only.
