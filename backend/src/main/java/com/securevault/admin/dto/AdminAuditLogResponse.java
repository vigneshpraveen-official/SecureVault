package com.securevault.admin.dto;

import java.time.Instant;

public record AdminAuditLogResponse(
        Long id,
        String action,
        String entityType,
        Long entityId,
        Long performedBy,
        Instant timestamp,
        String ipAddress,
        String userAgent,
        String details) {}
