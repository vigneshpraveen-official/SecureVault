package com.securevault.common.exception;

import org.springframework.http.HttpStatus;

/** Moved from com.securevault.user into the common hierarchy in S2.3 (M-26). */
public class DuplicateEmailException extends BusinessException {

    public DuplicateEmailException(String email) {
        super(ErrorCode.DUPLICATE_EMAIL, HttpStatus.CONFLICT, "Email already registered: " + email);
    }
}
