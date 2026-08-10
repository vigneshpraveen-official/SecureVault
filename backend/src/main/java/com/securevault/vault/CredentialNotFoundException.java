package com.securevault.vault;

/** Plain exception for this session — formalized in S2.3 (M-26) — TODO(S2.3). */
public class CredentialNotFoundException extends RuntimeException {

    public CredentialNotFoundException(Long id) {
        super("Credential not found: " + id);
    }
}
