package com.securevault.vault;

import com.securevault.vault.dto.CredentialCreateRequest;
import com.securevault.vault.dto.CredentialDetailResponse;
import com.securevault.vault.dto.CredentialResponse;
import com.securevault.vault.dto.CredentialSummaryResponse;
import com.securevault.vault.dto.CredentialUpdateRequest;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * Encryption/decryption never happens here (P2.1/M-24) — encryptedPassword and the decrypted
 * plaintext are always mapped explicitly in CredentialServiceImpl. This interface only moves the
 * plain fields; it stays free of business logic and crypto.
 */
@Mapper(componentModel = "spring")
public interface CredentialMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "encryptedPassword", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "favorite", ignore = true)
    @Mapping(target = "strengthScore", ignore = true)
    @Mapping(target = "passwordChangedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Credential toEntity(CredentialCreateRequest request);

    /**
     * Null fields on the request mean "leave unchanged" (D-established in S1.4) —
     * NullValuePropertyMappingStrategy.IGNORE makes MapStruct skip them automatically instead of
     * the hand-written null checks S1.4 used.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "encryptedPassword", ignore = true)
    @Mapping(target = "favorite", ignore = true)
    @Mapping(target = "strengthScore", ignore = true)
    @Mapping(target = "passwordChangedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromRequest(
            CredentialUpdateRequest request, @MappingTarget Credential credential);

    CredentialResponse toResponse(Credential credential);

    // historyCount is a second source param, not an entity field — same multi-source pattern as
    // toDetailResponse's decrypted password below. Callers must compute it via a batched query
    // (CredentialServiceImpl#toSummaryResponses), never a per-row lazy fetch (P4.4).
    @Mapping(target = "historyCount", source = "historyCount")
    CredentialSummaryResponse toSummaryResponse(Credential credential, long historyCount);

    @Mapping(target = "password", source = "decryptedPassword")
    CredentialDetailResponse toDetailResponse(Credential credential, String decryptedPassword);
}
