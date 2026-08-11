package com.securevault.vault.dto;

import com.securevault.vault.Category;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

/**
 * category is optional — defaults to OTHER in the service if omitted (S1.5). Full validation
 * coverage (P2.2/M-25) — see docs/validation.md. password deliberately has no @Pattern: this is a
 * secret for a third-party site the user does not control, not the SecureVault account password —
 * the app cannot demand it meet a complexity policy it has no authority over.
 */
public record CredentialCreateRequest(
        @NotBlank @Size(max = 150, message = "must be at most 150 characters") String title,
        @Size(max = 150, message = "must be at most 150 characters") String username,
        @NotBlank String password,
        @URL(message = "must be a valid URL")
                @Size(max = 255, message = "must be at most 255 characters")
                String websiteUrl,
        @Size(max = 2000, message = "must be at most 2000 characters") String notes,
        Category category) {}
