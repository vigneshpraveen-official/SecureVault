package com.securevault.monitoring.dto;

import java.time.Instant;

public record DeviceResponse(
        Long id,
        String deviceName,
        String ipAddress,
        String userAgent,
        Instant lastSeenAt,
        boolean trusted) {}
