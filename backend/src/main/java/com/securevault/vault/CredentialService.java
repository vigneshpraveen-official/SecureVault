package com.securevault.vault;

import com.securevault.common.response.PagedResponse;
import com.securevault.vault.dto.CredentialCreateRequest;
import com.securevault.vault.dto.CredentialDetailResponse;
import com.securevault.vault.dto.CredentialResponse;
import com.securevault.vault.dto.CredentialSummaryResponse;
import com.securevault.vault.dto.CredentialUpdateRequest;
import com.securevault.vault.dto.PasswordHistoryVersionResponse;
import com.securevault.vault.dto.VaultHealthResponse;
import java.util.List;

public interface CredentialService {

    CredentialResponse create(Long userId, CredentialCreateRequest request);

    CredentialDetailResponse getByIdForUser(Long id, Long userId);

    /** Version + timestamp only — never a historical password (P4.2). */
    List<PasswordHistoryVersionResponse> getPasswordHistory(Long id, Long userId);

    /**
     * All filters optional and freely combinable, ANDed together; owner and deleted=false are
     * always ANDed in regardless of what the caller passes (P4.5/M-34). sortBy is whitelisted by
     * the controller's Bean Validation before this is ever called.
     */
    PagedResponse<CredentialSummaryResponse> listForUser(
            Long userId,
            int page,
            int size,
            String sortBy,
            String direction,
            Category category,
            String title,
            String username,
            String website);

    List<CredentialSummaryResponse> search(Long userId, String term);

    CredentialResponse update(Long id, Long userId, CredentialUpdateRequest request);

    /** Soft delete (P4.3) — sets deleted=true/deletedAt=now(), never removes the row. */
    void delete(Long id, Long userId);

    /** No-op (200, current state) if already active — see ADR-018 for why not 409. */
    CredentialResponse restore(Long id, Long userId);

    List<CredentialSummaryResponse> trash(Long userId);

    /** Hard-deletes the credential AND its password history, in one transaction (P4.3). */
    void permanentDelete(Long id, Long userId);

    /**
     * Reuse detection decrypts each credential's password in memory, hashes it, and discards
     * everything immediately — never logs, caches, or returns plaintext (P3.3).
     */
    VaultHealthResponse getHealth(Long userId);

    /**
     * Fire-and-forget, off the request thread (P4.6) — resolves the TODO(S4.6) left since S3.3 for
     * bulk paths. Runs in its own transaction, not the caller's; userId is passed explicitly since
     * there is no request-bound security context on the async thread.
     */
    void recomputeStrengthForUser(Long userId);
}
