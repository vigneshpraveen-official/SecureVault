package com.securevault.user;

/**
 * Plain exception for this session (M-06/M-07). Moves into the common exception hierarchy +
 * GlobalExceptionHandler in S2.3 (M-26) — TODO(S2.3).
 */
public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException(String email) {
        super("Email already registered: " + email);
    }
}
