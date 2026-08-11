package com.securevault.monitoring;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Simple, explainable, no-ML rate counters in Redis (P5.5 step 5's "no ML, no magic" spirit applied
 * here too) — a rolling count per user in a fixed window, past a fixed threshold. Not persisted to
 * Postgres: these are cheap, high-frequency, ephemeral counters, the same category of state as the
 * MFA replay guard and JWT denylist, not an audit record in their own right (the SecurityAlert they
 * raise IS the durable record).
 */
@Service
@RequiredArgsConstructor
public class VaultAnomalyDetectorImpl implements VaultAnomalyDetector {

    private static final Duration ACCESS_WINDOW = Duration.ofMinutes(10);
    private static final long ACCESS_THRESHOLD = 50;
    private static final Duration DELETE_WINDOW = Duration.ofMinutes(10);
    private static final long DELETE_THRESHOLD = 5;

    private final StringRedisTemplate redisTemplate;
    private final SecurityAlertService securityAlertService;

    @Override
    public void recordAccess(Long userId) {
        checkAndAlert(
                userId,
                "vault:access:",
                ACCESS_WINDOW,
                ACCESS_THRESHOLD,
                AlertType.EXCESSIVE_VAULT_ACCESS,
                AlertSeverity.MEDIUM,
                "credential reads");
    }

    @Override
    public void recordPermanentDelete(Long userId) {
        checkAndAlert(
                userId,
                "vault:permdelete:",
                DELETE_WINDOW,
                DELETE_THRESHOLD,
                AlertType.MASS_PERMANENT_DELETE,
                AlertSeverity.HIGH,
                "permanent deletions");
    }

    private void checkAndAlert(
            Long userId,
            String keyPrefix,
            Duration window,
            long threshold,
            AlertType type,
            AlertSeverity severity,
            String label) {
        String countKey = keyPrefix + "count:" + userId;
        Long count = redisTemplate.opsForValue().increment(countKey);
        if (count != null && count == 1L) {
            redisTemplate.expire(countKey, window);
        }
        if (count == null || count < threshold) {
            return;
        }
        // setIfAbsent (Redis SETNX) makes the alert itself fire exactly once per window, not once
        // per request past the threshold — without this, every single subsequent call in the
        // same window would raise a duplicate alert.
        String alertedKey = keyPrefix + "alerted:" + userId;
        Boolean firstAlert = redisTemplate.opsForValue().setIfAbsent(alertedKey, "1", window);
        if (Boolean.TRUE.equals(firstAlert)) {
            securityAlertService.raise(
                    userId,
                    type,
                    severity,
                    count
                            + " "
                            + label
                            + " within "
                            + window.toMinutes()
                            + " minutes — above baseline");
        }
    }
}
