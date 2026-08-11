package com.securevault.security;

import com.securevault.security.dto.TokenRefreshResponse;

public interface RefreshTokenService {

    /** Issues a brand-new access+refresh pair, starting a fresh token family (login). */
    TokenRefreshResponse issue(Long userId, String deviceFingerprint);

    /**
     * Validates, rotates, and reissues from a presented raw refresh token (POST /api/auth/refresh).
     */
    TokenRefreshResponse refresh(String rawRefreshToken);

    /**
     * Revokes the refresh token identified by its raw value (POST /api/auth/logout). Idempotent.
     */
    void revoke(String rawRefreshToken);
}
