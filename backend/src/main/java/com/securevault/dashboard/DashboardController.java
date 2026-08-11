package com.securevault.dashboard;

import com.securevault.common.response.ApiResponse;
import com.securevault.dashboard.dto.DashboardPasswordHealthResponse;
import com.securevault.dashboard.dto.DashboardSummaryResponse;
import com.securevault.dashboard.dto.RecentActivityResponse;
import com.securevault.monitoring.SecurityAlertService;
import com.securevault.monitoring.dto.SecurityAlertResponse;
import com.securevault.security.UserPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Read-only aggregate analytics for the authenticated user")
public class DashboardController {

    private final DashboardService dashboardService;
    private final SecurityAlertService securityAlertService;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<DashboardSummaryResponse>> summary(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Dashboard summary computed", dashboardService.summary(principal.getId())));
    }

    @GetMapping("/password-health")
    public ResponseEntity<ApiResponse<DashboardPasswordHealthResponse>> passwordHealth(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Password health computed",
                        dashboardService.passwordHealth(principal.getId())));
    }

    @GetMapping("/recent-activity")
    public ResponseEntity<ApiResponse<List<RecentActivityResponse>>> recentActivity(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Recent activity retrieved",
                        dashboardService.recentActivity(principal.getId())));
    }

    // Same data as GET /api/monitoring/alerts (S5.5) — this route exists because P5.7 explicitly
    // lists it as its own dashboard widget endpoint; both delegate to the same SecurityAlertService
    // rather than duplicating the query.
    @GetMapping("/alerts")
    public ResponseEntity<ApiResponse<List<SecurityAlertResponse>>> alerts(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Unresolved alerts retrieved",
                        securityAlertService.listForUser(principal.getId())));
    }
}
