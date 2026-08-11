package com.securevault.common.exception;

/** The fixed error-code set from master §9 — every error response's errorCode is one of these. */
public enum ErrorCode {
    USER_NOT_FOUND,
    DUPLICATE_EMAIL,
    INVALID_CREDENTIALS,
    CREDENTIAL_NOT_FOUND,
    VALIDATION_FAILED,
    ACCESS_DENIED,
    PASSWORD_REUSED,
    SHARE_ALREADY_EXISTS,
    SELF_SHARE_NOT_ALLOWED,
    TOKEN_EXPIRED,
    TOKEN_INVALID,
    MFA_REQUIRED,
    MFA_INVALID,
    INTERNAL_ERROR
}
