# S5.3 — Redis caching: evidence

## Two bugs found live, both fixed and re-verified (full detail: ADR-025)

1. **`ClassCastException: LinkedHashMap cannot be cast to PagedResponse`** on the very first
   cache-hit read — `GenericJackson2JsonRedisSerializer` needs default typing; the app-wide REST
   `ObjectMapper` had none.
2. **`InvalidTypeIdException: missing type id`** after adding default typing with
   `DefaultTyping.NON_FINAL` — Java records are implicitly final, so records nested inside a
   generic field (`PagedResponse<T>.content`) never got a `@class` tag. Fixed with
   `DefaultTyping.EVERYTHING`.

## Vault list cache — hit/miss + eviction
```
GET /api/vault?page=0&size=5   -> 200 (MISS, populates cache)
  TRACE CacheInterceptor - No cache entry for key '...' in cache(s) [vaultList]
  TRACE CacheInterceptor - Creating cache entry for key '...' in cache(s) [vaultList]
GET /api/vault?page=0&size=5   -> 200 (HIT, identical data)
  TRACE CacheInterceptor - Cache entry for key '...' found in cache(s) [vaultList]
POST /api/vault {new credential} -> 201
  TRACE CacheInterceptor - Invalidating entire cache for operation ... create(...)
GET /api/vault?page=0&size=5   -> 200 (MISS again, totalElements now reflects the new item
                                        immediately — no stale read)
```

## Password strength cache — keyed by hash, never the password
```
POST /api/password/strength {"password":"..."}  -> 200
redis-cli KEYS 'sv:cache:passwordStrength*'
  -> sv:cache:passwordStrength::992c9c62b557675573f6d35e0dfe2db30ecf04efefd01ac4e95f41d76dc3a044
redis-cli GET <that key>
  -> {"@class":"...PasswordStrengthResponse","score":5,"strength":"Very Strong",
      "entropyBits":46.1,"feedback":[...]}
```
Key is a SHA-256 hex digest, not the password. Stored value contains only the analysis result.

`mvn clean verify` green after both fixes.
