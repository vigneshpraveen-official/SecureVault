package com.securevault.security;

import com.securevault.common.exception.TokenExpiredException;
import com.securevault.common.exception.TokenInvalidException;
import com.securevault.security.dto.TokenRefreshResponse;
import com.securevault.user.User;
import com.securevault.user.UserRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Value("${app.security.jwt-refresh-expiry-ms}")
    private long refreshExpiryMs;

    @Override
    @Transactional
    public TokenRefreshResponse issue(Long userId, String deviceFingerprint) {
        User user = userRepository.getReferenceById(userId);
        return mintPair(user, UUID.randomUUID().toString(), deviceFingerprint);
    }

    @Override
    // noRollbackFor is deliberate: @Transactional's default rolls back on any RuntimeException,
    // which would silently undo revokeFamily(...) below the moment TokenInvalidException is
    // thrown to signal the reuse to the caller — the exact opposite of what reuse detection
    // needs. The security side effect (the whole family revoked) must survive even though the
    // request itself ends in an error response. Found live: without this, replaying the family's
    // second-newest token after a detected reuse still succeeded (ADR-024).
    @Transactional(noRollbackFor = TokenInvalidException.class)
    public TokenRefreshResponse refresh(String rawRefreshToken) {
        String hash = TokenHasher.sha256Hex(rawRefreshToken);
        RefreshToken existing =
                refreshTokenRepository
                        .findByTokenHash(hash)
                        .orElseThrow(TokenInvalidException::new);

        if (existing.isRevoked()) {
            // Reuse detection (P5.2/ADR-024): this exact token was already rotated away once
            // before — someone is replaying an old refresh token, most likely a copy made before
            // it was last rotated (stolen, or a client bug re-sending a stale value). Revoking
            // the whole family invalidates every token descended from that original login, not
            // just this one, so the legitimate holder of the *current* token is forced to log in
            // again too — the only safe response once theft is suspected.
            int revokedCount = refreshTokenRepository.revokeFamily(existing.getTokenFamily());
            log.warn(
                    "Refresh token reuse detected: family={}, userId={}, tokensRevoked={}",
                    existing.getTokenFamily(),
                    existing.getUser().getId(),
                    revokedCount);
            throw new TokenInvalidException();
        }
        if (existing.getExpiresAt().isBefore(Instant.now())) {
            throw new TokenExpiredException();
        }

        existing.setRevoked(true);
        refreshTokenRepository.save(existing);

        User user = existing.getUser();
        log.info(
                "Refresh token rotated: userId={}, family={}",
                user.getId(),
                existing.getTokenFamily());
        // Rotation carries the device forward — a rotated token belongs to the same
        // session/device as the one it replaces (P5.4), so DELETE /api/monitoring/devices/{id}
        // keeps working across rotations, not just for the token minted at login.
        return mintPair(user, existing.getTokenFamily(), existing.getDeviceFingerprint());
    }

    @Override
    @Transactional
    public void revoke(String rawRefreshToken) {
        String hash = TokenHasher.sha256Hex(rawRefreshToken);
        // Idempotent, silent no-op if unknown (ADR-023 precedent) — logout must never leak
        // whether a given refresh token value was ever valid.
        refreshTokenRepository
                .findByTokenHash(hash)
                .ifPresent(
                        token -> {
                            token.setRevoked(true);
                            refreshTokenRepository.save(token);
                            log.info(
                                    "Refresh token revoked at logout: userId={}",
                                    token.getUser().getId());
                        });
    }

    private TokenRefreshResponse mintPair(User user, String family, String deviceFingerprint) {
        UserPrincipal principal = new UserPrincipal(user);
        String accessToken = jwtService.generateAccessToken(principal);

        String rawRefreshToken = TokenHasher.generateRawToken();
        RefreshToken refreshToken =
                RefreshToken.builder()
                        .user(user)
                        .tokenHash(TokenHasher.sha256Hex(rawRefreshToken))
                        .tokenFamily(family)
                        .deviceFingerprint(deviceFingerprint)
                        .expiresAt(Instant.now().plusMillis(refreshExpiryMs))
                        .revoked(false)
                        .build();
        refreshTokenRepository.save(refreshToken);

        return new TokenRefreshResponse(accessToken, rawRefreshToken);
    }
}
