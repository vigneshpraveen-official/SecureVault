package com.securevault.security.dto;

import com.securevault.user.Role;

/**
 * mfaRequired=true means: password was correct, tokens were NOT issued, and mfaChallengeToken must
 * be exchanged (with the current TOTP/backup code) via POST /api/auth/mfa/challenge for the real
 * accessToken/refreshToken (P5.4). All other fields are null in that case except the user's own
 * identity, which is not itself a secret and lets a client greet the right person mid-flow.
 */
public record LoginResponse(
        boolean mfaRequired,
        String mfaChallengeToken,
        String accessToken,
        String refreshToken,
        Long userId,
        String fullName,
        String email,
        Role role) {}
