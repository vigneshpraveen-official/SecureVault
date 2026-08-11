package com.securevault.vault.dto;

import com.securevault.vault.Category;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

/**
 * Every field is optional — null means "leave unchanged" (S1.4), so none of these use @NotBlank
 * (that would reject the very "field omitted" case the DTO exists to support). @Size/@URL still
 * apply when a field IS present — see docs/validation.md. Password is only re-encrypted if present
 * and different.
 */
public record CredentialUpdateRequest(
        @Size(min = 1, max = 150, message = "must be between 1 and 150 characters") String title,
        @Size(max = 150, message = "must be at most 150 characters") String username,
        String password,
        @URL(message = "must be a valid URL")
                @Size(max = 255, message = "must be at most 255 characters")
                String websiteUrl,
        @Size(max = 2000, message = "must be at most 2000 characters") String notes,
        Category category) {}
