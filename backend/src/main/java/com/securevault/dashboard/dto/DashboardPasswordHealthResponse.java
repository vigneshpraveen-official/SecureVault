package com.securevault.dashboard.dto;

import java.util.List;

public record DashboardPasswordHealthResponse(
        int healthScore,
        int veryWeakCount,
        int weakCount,
        int mediumCount,
        int strongCount,
        int veryStrongCount,
        int reusedPasswordCount,
        int staleCredentialCount,
        List<TopItemToFix> topItemsToFix) {}
