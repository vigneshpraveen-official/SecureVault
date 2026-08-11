package com.securevault.admin.dto;

import com.securevault.monitoring.AlertSeverity;
import java.util.Map;

public record AdminStatsResponse(
        long totalUsers,
        long activeSessions,
        long failedLogins24h,
        Map<AlertSeverity, Long> unresolvedAlertsBySeverity,
        String systemHealth) {}
