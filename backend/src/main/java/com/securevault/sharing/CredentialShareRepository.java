package com.securevault.sharing;

import com.securevault.sharing.dto.ShareResponse;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CredentialShareRepository extends JpaRepository<CredentialShare, Long> {

    Optional<CredentialShare> findByCredentialIdAndSharedWithUserIdAndActiveTrue(
            Long credentialId, Long sharedWithUserId);

    // Received: only what still actually grants access right now — an expired share is excluded
    // outright rather than returned with an "expired" flag, since from the recipient's side it
    // behaves exactly like no share (M-45).
    @Query(
            "SELECT new com.securevault.sharing.dto.ShareResponse(s.id, c.id, c.title, o.id,"
                    + " o.email, u.id, u.email, s.permission, s.sharedAt, s.expiresAt, s.active,"
                    + " false) "
                    + "FROM CredentialShare s JOIN s.credential c JOIN s.owner o JOIN"
                    + " s.sharedWithUser u "
                    + "WHERE u.id = :userId AND s.active = true AND c.deleted = false "
                    + "AND (s.expiresAt IS NULL OR s.expiresAt > CURRENT_TIMESTAMP) "
                    + "ORDER BY s.sharedAt DESC")
    List<ShareResponse> findReceivedByUserId(@Param("userId") Long userId);

    // Sent: the owner's management view — includes expired/soon-to-expire shares (flagged, not
    // hidden) so the owner can see and revoke them, unlike the recipient's received list above.
    @Query(
            "SELECT new com.securevault.sharing.dto.ShareResponse(s.id, c.id, c.title, o.id,"
                    + " o.email, u.id, u.email, s.permission, s.sharedAt, s.expiresAt, s.active,"
                    + " CASE WHEN s.expiresAt IS NOT NULL AND s.expiresAt <"
                    + " CURRENT_TIMESTAMP THEN true ELSE false END) "
                    + "FROM CredentialShare s JOIN s.credential c JOIN s.owner o JOIN"
                    + " s.sharedWithUser u "
                    + "WHERE o.id = :ownerId AND s.active = true AND c.deleted = false "
                    + "ORDER BY s.sharedAt DESC")
    List<ShareResponse> findSentByOwnerId(@Param("ownerId") Long ownerId);

    @Modifying
    @Query("DELETE FROM CredentialShare s WHERE s.credential.id = :credentialId")
    long deleteByCredentialId(@Param("credentialId") Long credentialId);
}
