package com.securevault.security;

import com.securevault.security.dto.MfaSetupResponse;
import com.securevault.security.dto.MfaVerifyResponse;
import com.securevault.user.User;

public interface MfaService {

    /** Generates and stores (AES-encrypted, not yet enabled) a fresh TOTP secret. */
    MfaSetupResponse setup(Long userId);

    /**
     * Confirms the first code against the just-set-up secret, enables MFA, and issues backup codes.
     */
    MfaVerifyResponse verify(Long userId, String code);

    /** Requires a currently-valid code (or backup code) — not just an authenticated session. */
    void disable(Long userId, String code);

    /** Used by the login challenge exchange — accepts either a live TOTP code or a backup code. */
    boolean verifyLoginCode(User user, String code);
}
