package com.securevault.common.exception;

import org.springframework.http.HttpStatus;

/** P5.1/M-45 — a user cannot share a credential with themselves. */
public class SelfShareNotAllowedException extends BusinessException {

    public SelfShareNotAllowedException() {
        super(
                ErrorCode.SELF_SHARE_NOT_ALLOWED,
                HttpStatus.BAD_REQUEST,
                "You cannot share a credential with yourself");
    }
}
