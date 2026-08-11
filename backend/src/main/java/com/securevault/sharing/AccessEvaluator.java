package com.securevault.sharing;

/**
 * Single authorisation decision point for vault operations (P5.1/M-44): is the caller the owner?
 * else is there an active, unexpired share, and what does its permission allow? Deliberately takes
 * primitive ids rather than a Credential/User entity — sharing must not depend on the vault
 * package's entity types, only the reverse.
 */
public interface AccessEvaluator {

    AccessLevel evaluate(Long credentialId, Long ownerId, Long userId);
}
