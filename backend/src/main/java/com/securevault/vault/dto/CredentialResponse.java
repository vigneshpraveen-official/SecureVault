package com.securevault.vault.dto;

import com.securevault.vault.Category;
import java.time.Instant;

/**
 * Never carries the password — used for create/update responses. List view uses
 * CredentialSummaryResponse; single-credential reveal uses CredentialDetailResponse. Built by
 * CredentialMapper (MapStruct), not a static factory (P2.1/M-24).
 */
public record CredentialResponse(
        Long id,
        String title,
        String username,
        String websiteUrl,
        String notes,
        Category category,
        boolean favorite,
        Instant createdAt,
        Instant updatedAt) {}
