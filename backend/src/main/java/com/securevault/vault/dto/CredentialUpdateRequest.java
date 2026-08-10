package com.securevault.vault.dto;

import com.securevault.vault.Category;

/**
 * Every field is optional — null means "leave unchanged." Password is only re-encrypted if present
 * and different.
 */
public record CredentialUpdateRequest(
        String title,
        String username,
        String password,
        String websiteUrl,
        String notes,
        Category category) {}
