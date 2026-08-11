package com.securevault.monitoring.dto;

import com.securevault.monitoring.AlertSeverity;
import com.securevault.monitoring.AlertType;
import java.time.Instant;

public record SecurityAlertResponse(
        Long id,
        AlertType type,
        AlertSeverity severity,
        String message,
        boolean resolved,
        Instant createdAt) {}
