package com.securevault.monitoring.dto;

import java.time.Instant;

public record LoginAttemptResponse(
        Long id,
        String email,
        boolean successful,
        String ipAddress,
        String userAgent,
        Instant attemptedAt,
        String failureReason) {}
