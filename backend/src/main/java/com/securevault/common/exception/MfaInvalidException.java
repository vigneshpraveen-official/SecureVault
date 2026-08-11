package com.securevault.common.exception;

import org.springframework.http.HttpStatus;

/**
 * P5.4 — a 6-digit code or backup code that failed verification, or an MFA challenge token that
 * doesn't resolve (expired, already consumed, or never existed). Deliberately one exception for all
 * three: none of them should let a caller distinguish "wrong code" from "expired challenge" from
 * "replayed code," the same anti-enumeration reasoning as InvalidCredentialsException.
 */
public class MfaInvalidException extends BusinessException {

    public MfaInvalidException() {
        super(
                ErrorCode.MFA_INVALID,
                HttpStatus.UNAUTHORIZED,
                "Invalid or expired MFA verification");
    }
}
