package com.securevault.dashboard;

import com.securevault.common.audit.AuditLog;
import com.securevault.common.audit.AuditLogRepository;
import com.securevault.dashboard.dto.DashboardPasswordHealthResponse;
import com.securevault.dashboard.dto.DashboardSummaryResponse;
import com.securevault.dashboard.dto.RecentActivityResponse;
import com.securevault.dashboard.dto.TopItemToFix;
import com.securevault.monitoring.LoginAttemptRepository;
import com.securevault.sharing.CredentialShareRepository;
import com.securevault.user.UserRepository;
import com.securevault.vault.Category;
import com.securevault.vault.Credential;
import com.securevault.vault.CredentialRepository;
import com.securevault.vault.CredentialService;
import com.securevault.vault.dto.VaultHealthResponse;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * P5.7: every count here is a database aggregate (grouped COUNT / existing repository projections),
 * never a full-table load followed by in-memory grouping — the one exception is passwordHealth()'s
 * "top 5 to fix" ranking, which sorts a single user's own already-bounded active-credential list
 * (the same list CredentialServiceImpl.getHealth() already loads for the exact same user, same
 * established precedent).
 */
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private static final int TOP_ITEMS_TO_FIX = 5;

    private final CredentialRepository credentialRepository;
    private final CredentialShareRepository credentialShareRepository;
    private final CredentialService credentialService;
    private final LoginAttemptRepository loginAttemptRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    // Cached briefly (P5.3/S5.7 — "cache aggregates briefly, document the staleness window"):
    // 2-minute TTL, RedisCacheConfig's "dashboard" region. Time-based expiry only, no active
    // eviction on every vault/share mutation — a dashboard summary being up to 2 minutes stale is
    // an accepted, documented tradeoff, unlike vaultList/shares which evict immediately because
    // stale access-control state there would be a real security problem, not a UX nit.
    @Override
    @Cacheable(cacheNames = "dashboard", key = "'summary:' + #userId")
    public DashboardSummaryResponse summary(Long userId) {
        Map<Category, Long> byCategory = new HashMap<>();
        for (Object[] row : credentialRepository.countByCategoryForUser(userId)) {
            byCategory.put((Category) row[0], (Long) row[1]);
        }
        long total = credentialRepository.countByUserIdAndDeletedFalse(userId);
        long favorites = credentialRepository.countByUserIdAndDeletedFalseAndFavoriteTrue(userId);
        long trash = credentialRepository.findByUserIdAndDeletedTrue(userId).size();
        long sharedIn = credentialShareRepository.findReceivedByUserId(userId).size();
        long sharedOut = credentialShareRepository.findSentByOwnerId(userId).size();
        String email = userRepository.findById(userId).map(u -> u.getEmail()).orElse(null);
        var lastLogin =
                email == null
                        ? null
                        : loginAttemptRepository.findLatestSuccessTime(email).orElse(null);

        return new DashboardSummaryResponse(
                total, byCategory, favorites, sharedIn, sharedOut, trash, lastLogin);
    }

    @Override
    @Cacheable(cacheNames = "dashboard", key = "'passwordHealth:' + #userId")
    public DashboardPasswordHealthResponse passwordHealth(Long userId) {
        VaultHealthResponse health = credentialService.getHealth(userId);

        List<TopItemToFix> topItems =
                credentialRepository.findByUserIdAndDeletedFalse(userId).stream()
                        .sorted(
                                Comparator.comparing(
                                                (Credential c) ->
                                                        c.getStrengthScore() == null
                                                                ? -1
                                                                : (int) c.getStrengthScore())
                                        .thenComparing(Credential::getPasswordChangedAt))
                        .limit(TOP_ITEMS_TO_FIX)
                        .map(
                                c ->
                                        new TopItemToFix(
                                                c.getId(),
                                                c.getTitle(),
                                                c.getStrengthScore() == null
                                                        ? "Not yet scored"
                                                        : "Strength score "
                                                                + c.getStrengthScore()
                                                                + "/5"))
                        .toList();

        return new DashboardPasswordHealthResponse(
                health.healthScore(),
                health.veryWeakCount(),
                health.weakCount(),
                health.mediumCount(),
                health.strongCount(),
                health.veryStrongCount(),
                health.reusedPasswordCount(),
                health.staleCredentialCount(),
                topItems);
    }

    // Not cached (P5.3): an activity feed reads stale immediately in a way a count doesn't —
    // showing yesterday's aggregate is fine, showing an action from 90 seconds ago as "not
    // happened yet" reads as broken to whoever just did it.
    @Override
    public List<RecentActivityResponse> recentActivity(Long userId) {
        return auditLogRepository.findTop20ByPerformedByOrderByTimestampDesc(userId).stream()
                .map(DashboardServiceImpl::toResponse)
                .toList();
    }

    private static RecentActivityResponse toResponse(AuditLog log) {
        String entity = log.getEntityType().toLowerCase().replace('_', ' ');
        String description =
                switch (log.getAction()) {
                    case CREATE -> "Created " + entity;
                    case UPDATE -> "Updated " + entity;
                    case DELETE -> "Moved " + entity + " to trash";
                    case RESTORE -> "Restored " + entity + " from trash";
                    case PERMANENT_DELETE -> "Permanently deleted " + entity;
                    case ACCESS -> "Viewed a shared " + entity;
                    case SHARE -> "Shared " + entity;
                    case REVOKE -> "Revoked access to " + entity;
                };
        return new RecentActivityResponse(
                log.getId(),
                log.getAction().name(),
                log.getEntityType(),
                log.getEntityId(),
                description,
                log.getTimestamp());
    }
}
