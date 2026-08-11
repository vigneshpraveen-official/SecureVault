package com.securevault.common.exception;

import org.springframework.http.HttpStatus;

/** P5.1/M-45 — this credential already has an active share with this user. */
public class ShareAlreadyExistsException extends BusinessException {

    public ShareAlreadyExistsException() {
        super(
                ErrorCode.SHARE_ALREADY_EXISTS,
                HttpStatus.CONFLICT,
                "This credential is already shared with that user");
    }
}
