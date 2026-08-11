package com.securevault.vault.dto;

import com.securevault.vault.Category;
import java.time.Instant;

/**
 * List-view shape (GET /api/vault, GET /api/vault/search) — kept as its own type, distinct from
 * CredentialResponse, so the list contract can evolve (e.g. drop a field for payload size) without
 * touching the single-credential create/update contract (P2.1/M-24: API contract stability). Never
 * carries the password.
 */
public record CredentialSummaryResponse(
        Long id,
        String title,
        String username,
        String websiteUrl,
        String notes,
        Category category,
        boolean favorite,
        Integer strengthScore,
        Instant createdAt,
        Instant updatedAt) {}
