package com.securevault.dashboard;

import com.securevault.dashboard.dto.DashboardPasswordHealthResponse;
import com.securevault.dashboard.dto.DashboardSummaryResponse;
import com.securevault.dashboard.dto.RecentActivityResponse;
import java.util.List;

public interface DashboardService {

    DashboardSummaryResponse summary(Long userId);

    DashboardPasswordHealthResponse passwordHealth(Long userId);

    List<RecentActivityResponse> recentActivity(Long userId);
}
