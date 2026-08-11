package com.securevault.vault;

import com.securevault.vault.dto.CredentialCreateRequest;
import com.securevault.vault.dto.CredentialDetailResponse;
import com.securevault.vault.dto.CredentialResponse;
import com.securevault.vault.dto.CredentialSummaryResponse;
import com.securevault.vault.dto.CredentialUpdateRequest;
import com.securevault.vault.dto.VaultHealthResponse;
import java.util.List;

public interface CredentialService {

    CredentialResponse create(Long userId, CredentialCreateRequest request);

    CredentialDetailResponse getByIdForUser(Long id, Long userId);

    /**
     * category is nullable — null returns every credential. Shaped to absorb more optional filters
     * in S4.5 without a rewrite.
     */
    List<CredentialSummaryResponse> listForUser(Long userId, Category category);

    List<CredentialSummaryResponse> search(Long userId, String term);

    CredentialResponse update(Long id, Long userId, CredentialUpdateRequest request);

    void delete(Long id, Long userId);

    /**
     * Reuse detection decrypts each credential's password in memory, hashes it, and discards
     * everything immediately — never logs, caches, or returns plaintext (P3.3).
     */
    VaultHealthResponse getHealth(Long userId);
}
