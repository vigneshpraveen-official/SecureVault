package com.securevault.dashboard.dto;

import java.time.Instant;

public record RecentActivityResponse(
        Long id,
        String action,
        String entityType,
        Long entityId,
        String description,
        Instant timestamp) {}
