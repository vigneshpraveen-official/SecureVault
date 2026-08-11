package com.securevault.vault.dto;

/**
 * Aggregate password health for the authenticated user's whole vault (S3.3). Formula for every
 * field documented in docs/password-policy.md §3. Never carries a password, a hash, or any
 * per-credential identifying detail — aggregate counts only.
 */
public record VaultHealthResponse(
        int totalCredentials,
        int veryWeakCount,
        int weakCount,
        int mediumCount,
        int strongCount,
        int veryStrongCount,
        int reusedPasswordCount,
        int staleCredentialCount,
        int healthScore) {}
