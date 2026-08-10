package com.securevault.vault.dto;

import com.securevault.vault.Category;
import com.securevault.vault.Credential;
import java.time.Instant;

/** Single-credential reveal only (GET /api/vault/{id}) — never used in a list response. */
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
        Instant updatedAt) {

    public static CredentialDetailResponse from(Credential credential, String decryptedPassword) {
        return new CredentialDetailResponse(
                credential.getId(),
                credential.getTitle(),
                credential.getUsername(),
                decryptedPassword,
                credential.getWebsiteUrl(),
                credential.getNotes(),
                credential.getCategory(),
                credential.isFavorite(),
                credential.getCreatedAt(),
                credential.getUpdatedAt());
    }
}
