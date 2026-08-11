package com.securevault.vault;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, Long> {

    // Reuse-prevention window is exactly 5 (P4.2) — Top5 caps it at the query level, not by
    // fetching everything and truncating in Java.
    List<PasswordHistory> findTop5ByCredentialIdOrderByVersionDesc(Long credentialId);

    Optional<PasswordHistory> findFirstByCredentialIdOrderByVersionDesc(Long credentialId);

    // GET /api/vault/{id}/history projects onto PasswordHistoryVersionResponse directly in the
    // JPQL SELECT — the encrypted_password column is never even fetched into memory for this
    // query, which is a stronger guarantee than "the DTO mapper happens not to expose it"
    // (P4.2's "not even decrypted, not even to the owner" hardening).
    @Query(
            "SELECT new com.securevault.vault.dto.PasswordHistoryVersionResponse(ph.version,"
                    + " ph.createdAt) "
                    + "FROM PasswordHistory ph WHERE ph.credential.id = :credentialId "
                    + "ORDER BY ph.version ASC")
    List<com.securevault.vault.dto.PasswordHistoryVersionResponse> findVersionsByCredentialId(
            @Param("credentialId") Long credentialId);

    long deleteByCredentialId(Long credentialId);

    // S4.4/M-33 N+1 fix: one aggregate query for however many credentials are on a page, instead
    // of one COUNT per credential. See docs/evidence/milestone-2/n-plus-one.md.
    @Query(
            "SELECT ph.credential.id, COUNT(ph) FROM PasswordHistory ph "
                    + "WHERE ph.credential.id IN :credentialIds GROUP BY ph.credential.id")
    List<Object[]> countByCredentialIds(@Param("credentialIds") List<Long> credentialIds);
}
