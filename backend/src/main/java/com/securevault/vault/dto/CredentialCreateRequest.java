package com.securevault.vault.dto;

import com.securevault.vault.Category;
import jakarta.validation.constraints.NotBlank;

/**
 * category is optional — defaults to OTHER in the service if omitted (S1.5). Minimal validation
 * added in S1.6 to close a Milestone 1 quality gap. Full coverage is S2.2 (M-25).
 */
public record CredentialCreateRequest(
        @NotBlank String title,
        String username,
        @NotBlank String password,
        String websiteUrl,
        String notes,
        Category category) {}
