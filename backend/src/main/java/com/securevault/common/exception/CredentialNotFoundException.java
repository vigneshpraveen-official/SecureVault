package com.securevault.common.exception;

import org.springframework.http.HttpStatus;

/** Moved from com.securevault.vault into the common hierarchy in S2.3 (M-26). */
public class CredentialNotFoundException extends BusinessException {

    public CredentialNotFoundException(Long id) {
        super(ErrorCode.CREDENTIAL_NOT_FOUND, HttpStatus.NOT_FOUND, "Credential not found: " + id);
    }
}
