package com.securevault.vault;

/**
 * Raised when a credential exists but does not belong to the authenticated user — deliberately
 * distinct from CredentialNotFoundException (404). Formalized in S2.3 (M-26) — TODO(S2.3).
 */
public class CredentialAccessDeniedException extends RuntimeException {

    public CredentialAccessDeniedException() {
        super("You do not have access to this credential");
    }
}
