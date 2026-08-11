package com.securevault.monitoring;

import com.securevault.monitoring.dto.DeviceResponse;
import java.util.List;

public interface DeviceService {

    /**
     * Upserts the device row for this login and returns true iff this fingerprint is new for the
     * user.
     */
    boolean recordLogin(
            Long userId, String fingerprint, String deviceName, String ip, String userAgent);

    List<DeviceResponse> list(Long userId);

    /** Owner-only; revokes every refresh token this device ever minted and forgets the device. */
    void revoke(Long deviceId, Long userId);
}
