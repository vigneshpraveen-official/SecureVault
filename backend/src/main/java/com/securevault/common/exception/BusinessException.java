package com.securevault.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base for every domain-level error condition SecureVault raises deliberately (as opposed to an
 * unexpected bug). Carries the ErrorCode and HttpStatus the client actually needs, so
 * GlobalExceptionHandler can render any subclass with one generic handler instead of one
 * per-controller handler per exception (P2.3/M-26).
 */
@Getter
public abstract class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus httpStatus;

    protected BusinessException(ErrorCode errorCode, HttpStatus httpStatus, String message) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
}
