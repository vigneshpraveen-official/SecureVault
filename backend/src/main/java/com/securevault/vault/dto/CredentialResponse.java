package com.securevault.vault.dto;

import com.securevault.vault.Category;
import com.securevault.vault.Credential;
import java.time.Instant;

/**
 * Never carries the password — used for create and list. Single-credential reveal uses
 * CredentialDetailResponse.
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
        Instant updatedAt) {

    public static CredentialResponse from(Credential credential) {
        return new CredentialResponse(
                credential.getId(),
                credential.getTitle(),
                credential.getUsername(),
                credential.getWebsiteUrl(),
                credential.getNotes(),
                credential.getCategory(),
                credential.isFavorite(),
                credential.getCreatedAt(),
                credential.getUpdatedAt());
    }
}
