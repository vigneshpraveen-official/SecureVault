package com.securevault.common.exception;

import org.springframework.http.HttpStatus;

/**
 * P5.2 — a refresh token that doesn't resolve to any known token, or that resolves to one already
 * revoked (rotated-away or reused after theft-detection revoked its whole family).
 */
public class TokenInvalidException extends BusinessException {

    public TokenInvalidException() {
        super(ErrorCode.TOKEN_INVALID, HttpStatus.UNAUTHORIZED, "Refresh token is invalid");
    }
}
