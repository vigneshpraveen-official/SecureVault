package com.securevault.common.exception;

import org.springframework.http.HttpStatus;

/** P5.2 — a refresh token that is structurally valid (found, right hash) but past its expiry. */
public class TokenExpiredException extends BusinessException {

    public TokenExpiredException() {
        super(ErrorCode.TOKEN_EXPIRED, HttpStatus.UNAUTHORIZED, "Refresh token has expired");
    }
}
