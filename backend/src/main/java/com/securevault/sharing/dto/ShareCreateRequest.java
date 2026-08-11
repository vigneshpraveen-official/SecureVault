package com.securevault.sharing.dto;

import com.securevault.sharing.SharePermission;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record ShareCreateRequest(
        @NotNull Long credentialId,
        @NotBlank @Email String sharedWithEmail,
        @NotNull SharePermission permission,
        Instant expiresAt) {}
