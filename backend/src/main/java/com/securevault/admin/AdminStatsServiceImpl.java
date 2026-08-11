package com.securevault.admin;

import com.securevault.admin.dto.AdminStatsResponse;
import com.securevault.monitoring.AlertSeverity;
import com.securevault.monitoring.LoginAttemptRepository;
import com.securevault.monitoring.SecurityAlertRepository;
import com.securevault.security.RefreshTokenRepository;
import com.securevault.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Deliberately a separate bean from AdminController (P5.7/ADR-025 precedent): @Cacheable
 * short-circuits the ENTIRE intercepted method on a cache hit, including anything inside it — if
 * the ADMIN role check lived in this same cached method, a cache hit within the 2-minute TTL would
 * skip the check and hand a non-admin the cached response. Keeping the check in the never-cached
 * controller and the computation here (no per-caller data, safe to cache and share across admins)
 * is what makes "every admin endpoint returns 403 for non-admins" (P5.7) actually hold under
 * caching, not just on a cache miss.
 */
@Service
@RequiredArgsConstructor
public class AdminStatsServiceImpl implements AdminStatsService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final SecurityAlertRepository securityAlertRepository;

    @Override
    @Cacheable(cacheNames = "dashboard", key = "'admin-stats'")
    public AdminStatsResponse computeStats() {
        long totalUsers = userRepository.count();
        long activeSessions =
                refreshTokenRepository.countByRevokedFalseAndExpiresAtAfter(Instant.now());
        long failedLogins24h =
                loginAttemptRepository.countBySuccessfulFalseAndAttemptedAtAfter(
                        Instant.now().minus(Duration.ofHours(24)));

        Map<AlertSeverity, Long> bySeverity = new HashMap<>();
        for (Object[] row : securityAlertRepository.countUnresolvedBySeverity()) {
            bySeverity.put((AlertSeverity) row[0], (Long) row[1]);
        }

        return new AdminStatsResponse(
                totalUsers, activeSessions, failedLogins24h, bySeverity, "UP");
    }
}
