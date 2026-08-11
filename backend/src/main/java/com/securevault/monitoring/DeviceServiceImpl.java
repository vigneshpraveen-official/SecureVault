package com.securevault.monitoring;

import com.securevault.common.exception.AccessDeniedException;
import com.securevault.monitoring.dto.DeviceResponse;
import com.securevault.security.RefreshTokenRepository;
import com.securevault.user.UserRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceServiceImpl implements DeviceService {

    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SecurityAlertService securityAlertService;

    @Override
    @Transactional
    public boolean recordLogin(
            Long userId, String fingerprint, String deviceName, String ip, String userAgent) {
        return deviceRepository
                .findByUserIdAndDeviceFingerprint(userId, fingerprint)
                .map(
                        existing -> {
                            existing.setLastSeenAt(Instant.now());
                            existing.setIpAddress(ip);
                            existing.setUserAgent(userAgent);
                            deviceRepository.save(existing);
                            return false;
                        })
                .orElseGet(
                        () -> {
                            // Checked BEFORE inserting the new row — the account's very first
                            // device ever is not an anomaly (P5.5 step 3: "new device" is only
                            // meaningful relative to an established baseline of at least one).
                            boolean hadPriorDevice = deviceRepository.countByUserId(userId) > 0;

                            Device device =
                                    Device.builder()
                                            .user(userRepository.getReferenceById(userId))
                                            .deviceFingerprint(fingerprint)
                                            .deviceName(deviceName)
                                            .ipAddress(ip)
                                            .userAgent(userAgent)
                                            .lastSeenAt(Instant.now())
                                            .trusted(true)
                                            .build();
                            deviceRepository.save(device);
                            log.warn(
                                    "Login from new device: userId={}, fingerprint={}",
                                    userId,
                                    fingerprint);
                            if (hadPriorDevice) {
                                securityAlertService.raise(
                                        userId,
                                        AlertType.NEW_DEVICE,
                                        AlertSeverity.MEDIUM,
                                        "Login from a previously unseen device or network");
                            }
                            return true;
                        });
    }

    @Override
    public List<DeviceResponse> list(Long userId) {
        return deviceRepository.findByUserIdOrderByLastSeenAtDesc(userId).stream()
                .map(
                        d ->
                                new DeviceResponse(
                                        d.getId(),
                                        d.getDeviceName(),
                                        d.getIpAddress(),
                                        d.getUserAgent(),
                                        d.getLastSeenAt(),
                                        d.isTrusted()))
                .toList();
    }

    @Override
    @Transactional
    public void revoke(Long deviceId, Long userId) {
        // Not-found/not-owned collapse into one 403 (ADR-023 precedent) — no DEVICE_NOT_FOUND
        // code in master §9's fixed ErrorCode enum.
        Device device = deviceRepository.findById(deviceId).orElseThrow(AccessDeniedException::new);
        if (!device.getUser().getId().equals(userId)) {
            throw new AccessDeniedException();
        }
        int revokedTokens =
                refreshTokenRepository.revokeByUserAndDeviceFingerprint(
                        userId, device.getDeviceFingerprint());
        deviceRepository.delete(device);
        log.info(
                "Device revoked: deviceId={}, userId={}, refreshTokensRevoked={}",
                deviceId,
                userId,
                revokedTokens);
    }
}
