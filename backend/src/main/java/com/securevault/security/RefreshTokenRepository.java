package com.securevault.security;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // P5.7 admin stats: an "active session" is a not-yet-revoked, not-yet-expired refresh token —
    // there is no separate session table (D-13's Redis holds only the access-token denylist).
    long countByRevokedFalseAndExpiresAtAfter(Instant now);

    // Reuse detection (P5.2): one statement revokes every token descended from the same login,
    // not just the replayed one — a stolen-and-already-used token can't be used to mint a fresh
    // chain once the theft is detected.
    @Modifying
    @Query(
            "UPDATE RefreshToken t SET t.revoked = true WHERE t.tokenFamily = :family AND t.revoked = false")
    int revokeFamily(@Param("family") String tokenFamily);

    // P5.4 — "DELETE revokes a device's sessions": every non-revoked refresh token this device
    // ever minted, so a stolen/shared device can be cut off without touching the user's others.
    @Modifying
    @Query(
            "UPDATE RefreshToken t SET t.revoked = true WHERE t.user.id = :userId AND"
                    + " t.deviceFingerprint = :fingerprint AND t.revoked = false")
    int revokeByUserAndDeviceFingerprint(
            @Param("userId") Long userId, @Param("fingerprint") String fingerprint);
}
