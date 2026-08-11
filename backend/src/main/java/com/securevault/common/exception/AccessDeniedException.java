package com.securevault.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Generic ownership/permission 403 — replaces S1.4's CredentialAccessDeniedException in S2.3
 * (M-26). One shared ACCESS_DENIED error code covers every entity (master §9 defines only one such
 * code), unlike "not found," which stays per-entity because each has its own error code
 * (USER_NOT_FOUND, CREDENTIAL_NOT_FOUND, ...). Named to match the ErrorCode/master §9 wording;
 * distinct from org.springframework.security.access.AccessDeniedException (the framework's own
 * type, thrown by method security) — GlobalExceptionHandler handles both, separately.
 */
public class AccessDeniedException extends BusinessException {

    public AccessDeniedException(String message) {
        super(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, message);
    }

    public AccessDeniedException() {
        this("You do not have access to this resource");
    }
}
