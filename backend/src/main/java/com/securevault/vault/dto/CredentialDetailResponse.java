package com.securevault.vault.dto;

import com.securevault.vault.Category;
import java.time.Instant;

/**
 * Single-credential reveal only (GET /api/vault/{id}) — never used in a list response. Built by
 * CredentialMapper (MapStruct) from the entity plus the already-decrypted plaintext; decryption
 * itself stays in the service, never in the mapper (P2.1/M-24).
 */
public record CredentialDetailResponse(
        Long id,
        String title,
        String username,
        String password,
        String websiteUrl,
        String notes,
        Category category,
        boolean favorite,
        Instant createdAt,
        Instant updatedAt) {}
