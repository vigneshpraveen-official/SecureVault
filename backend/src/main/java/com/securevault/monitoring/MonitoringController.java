package com.securevault.monitoring;

import com.securevault.common.response.ApiResponse;
import com.securevault.monitoring.dto.LoginAttemptResponse;
import com.securevault.monitoring.dto.RiskScoreResponse;
import com.securevault.monitoring.dto.SecurityAlertResponse;
import com.securevault.security.UserPrincipal;
import com.securevault.user.Role;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitoring")
@RequiredArgsConstructor
@Tag(name = "Monitoring", description = "Login attempts, security alerts, risk score, and devices")
public class MonitoringController {

    private final LoginAttemptService loginAttemptService;
    private final SecurityAlertService securityAlertService;

    // ?all=true is ADMIN-only (P5.5 step 4: "own history; admin sees all"); a non-admin passing
    // it just silently gets their own history back rather than a 403 — method security
    // (@PreAuthorize) isn't enabled until S5.8, so this is a plain manual role check for now.
    @GetMapping("/login-attempts")
    public ResponseEntity<ApiResponse<List<LoginAttemptResponse>>> loginAttempts(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "false") boolean all) {
        List<LoginAttemptResponse> attempts =
                (all
                                && principal.getAuthorities().stream()
                                        .anyMatch(
                                                a -> a.getAuthority().equals("ROLE_" + Role.ADMIN)))
                        ? loginAttemptService.listAll()
                        : loginAttemptService.listForUser(principal.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Login attempts retrieved", attempts));
    }

    @GetMapping("/alerts")
    public ResponseEntity<ApiResponse<List<SecurityAlertResponse>>> alerts(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "false") boolean all) {
        List<SecurityAlertResponse> alerts =
                (all
                                && principal.getAuthorities().stream()
                                        .anyMatch(
                                                a -> a.getAuthority().equals("ROLE_" + Role.ADMIN)))
                        ? securityAlertService.listAll()
                        : securityAlertService.listForUser(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Security alerts retrieved", alerts));
    }

    // Simple, documented, explainable formula (P5.5 step 5 — "no ML, no magic"):
    // +30 if the account is currently locked
    // +10 per failed login attempt in the last 24h, capped at 40
    // +15 per unresolved security alert, capped at 45
    // clamped to [0, 100]. Not persisted — computed fresh on every call from live data.
    @GetMapping("/risk-score")
    public ResponseEntity<ApiResponse<RiskScoreResponse>> riskScore(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<SecurityAlertResponse> unresolved =
                securityAlertService.listForUser(principal.getId());
        long recentFailures =
                loginAttemptService.listForUser(principal.getUsername()).stream()
                        .filter(a -> !a.successful())
                        .filter(
                                a ->
                                        a.attemptedAt()
                                                .isAfter(Instant.now().minus(Duration.ofHours(24))))
                        .count();

        int score = 0;
        List<String> factors = new ArrayList<>();
        if (!principal.isAccountNonLocked()) {
            score += 30;
            factors.add("account currently locked");
        }
        int failurePoints = (int) Math.min(40, recentFailures * 10);
        if (failurePoints > 0) {
            score += failurePoints;
            factors.add(recentFailures + " failed login attempt(s) in the last 24h");
        }
        int alertPoints = (int) Math.min(45, unresolved.size() * 15L);
        if (alertPoints > 0) {
            score += alertPoints;
            factors.add(unresolved.size() + " unresolved security alert(s)");
        }
        score = Math.max(0, Math.min(100, score));

        return ResponseEntity.ok(
                ApiResponse.success("Risk score computed", new RiskScoreResponse(score, factors)));
    }
}
