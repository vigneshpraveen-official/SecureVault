package com.securevault.common.exception;

import org.springframework.http.HttpStatus;

/** P4.2/M-36 — the incoming password matches one of the last 5 for this credential. */
public class PasswordReusedException extends BusinessException {

    public PasswordReusedException() {
        super(
                ErrorCode.PASSWORD_REUSED,
                HttpStatus.CONFLICT,
                "This password was used recently for this credential — choose a different one");
    }
}
