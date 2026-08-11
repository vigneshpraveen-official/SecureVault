package com.securevault.sharing.dto;

import com.securevault.sharing.SharePermission;
import java.time.Instant;

/**
 * Built directly by a JPQL constructor-expression projection (CredentialShareRepository) — never
 * assembled by walking lazy Credential/User associations after the fact, same N+1-avoidance
 * reasoning as P4.4.
 */
public record ShareResponse(
        Long id,
        Long credentialId,
        String credentialTitle,
        Long ownerId,
        String ownerEmail,
        Long sharedWithUserId,
        String sharedWithEmail,
        SharePermission permission,
        Instant sharedAt,
        Instant expiresAt,
        boolean active,
        boolean expired) {}
