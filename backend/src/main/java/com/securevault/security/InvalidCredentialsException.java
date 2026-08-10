package com.securevault.security;

/**
 * Plain exception for this session (M-18/M-19). Moves into the common exception hierarchy +
 * GlobalExceptionHandler in S2.3 (M-26) — TODO(S2.3). Deliberately carries no detail about which
 * field was wrong.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
