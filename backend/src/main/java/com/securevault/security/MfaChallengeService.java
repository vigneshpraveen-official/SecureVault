package com.securevault.security;

public interface MfaChallengeService {

    /** Creates a short-lived (2 min) challenge token for a password-verified, MFA-pending login. */
    String createChallenge(Long userId);

    /**
     * Resolves the token's userId WITHOUT deleting it — a wrong code must not burn the token, so
     * the caller can retry with a different code until the 2-minute TTL runs out. Throws
     * MfaInvalidException if missing/expired.
     */
    Long peekChallenge(String challengeToken);

    /**
     * Deletes the token — call only after a code has actually verified, making it truly one-shot.
     */
    void invalidateChallenge(String challengeToken);
}
