package com.securevault.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Moved from com.securevault.security into the common hierarchy in S2.3 (M-26). Deliberately
 * carries no detail about which field (email vs. password) was wrong.
 */
public class InvalidCredentialsException extends BusinessException {

    public InvalidCredentialsException() {
        super(ErrorCode.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }
}
