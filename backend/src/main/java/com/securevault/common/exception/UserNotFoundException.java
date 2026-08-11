package com.securevault.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Not yet thrown anywhere in Phase 1-2 code — login intentionally reports
 * InvalidCredentialsException instead so it never discloses whether an email exists (S1.2). Defined
 * now, per P2.3/M-26, ready for the direct user-lookup endpoints later phases (e.g. admin console,
 * S5.8) will add.
 */
public class UserNotFoundException extends BusinessException {

    public UserNotFoundException(Long id) {
        super(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND, "User not found: " + id);
    }

    // P5.1: sharing looks a recipient up by email, not id — the sharing prompt (P5.1 step 2)
    // requires resolving `sharedWithEmail`, so unlike login this endpoint does disclose whether
    // an email is registered; that's an accepted, documented tradeoff for a feature that
    // inherently needs it (ADR-023).
    public UserNotFoundException(String email) {
        super(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND, "User not found: " + email);
    }
}
