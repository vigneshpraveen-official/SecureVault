package com.securevault.dashboard.dto;

import com.securevault.vault.Category;
import java.time.Instant;
import java.util.Map;

public record DashboardSummaryResponse(
        long totalCredentials,
        Map<Category, Long> byCategory,
        long favoritesCount,
        long sharedInCount,
        long sharedOutCount,
        long trashCount,
        Instant lastLogin) {}
