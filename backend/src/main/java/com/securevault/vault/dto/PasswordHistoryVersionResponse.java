package com.securevault.vault.dto;

import java.time.Instant;

/**
 * Version and timestamp ONLY — never the historical password, not even decrypted, not even to the
 * owner (P4.2's explicit hardening). Built directly by a JPQL constructor-expression query that
 * never selects the encrypted_password column at all, not just a DTO that omits the field.
 */
public record PasswordHistoryVersionResponse(Integer version, Instant createdAt) {}
