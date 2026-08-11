package com.securevault.notification;

import com.securevault.vault.CredentialRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * P5.6 step 2's "password expiry warning (credentials older than 90 days)" trigger. Same 90-day
 * threshold as CredentialServiceImpl.getHealth()'s STALE_AFTER_DAYS. Rate-limited to once per user
 * per 7 days (Redis, same pattern as VaultAnomalyDetectorImpl) — without this, a daily sweep would
 * re-notify about the same stale credentials every single day.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordExpiryCheckServiceImpl implements PasswordExpiryCheckService {

    private static final int STALE_AFTER_DAYS = 90;
    private static final String NOTIFIED_KEY_PREFIX = "password-expiry:notified:";
    private static final Duration RENOTIFY_INTERVAL = Duration.ofDays(7);

    private final CredentialRepository credentialRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final StringRedisTemplate redisTemplate;

    @Override
    @Transactional(readOnly = true)
    public void checkAndNotify() {
        Instant threshold = Instant.now().minus(STALE_AFTER_DAYS, ChronoUnit.DAYS);
        List<Object[]> rows = credentialRepository.countStaleCredentialsByUser(threshold);
        int notified = 0;
        for (Object[] row : rows) {
            Long userId = (Long) row[0];
            long staleCount = (Long) row[1];
            String key = NOTIFIED_KEY_PREFIX + userId;
            Boolean firstThisWindow =
                    redisTemplate.opsForValue().setIfAbsent(key, "1", RENOTIFY_INTERVAL);
            if (Boolean.TRUE.equals(firstThisWindow)) {
                eventPublisher.publishEvent(
                        new PasswordExpiryWarningEvent(userId, (int) staleCount));
                notified++;
            }
        }
        log.info(
                "Password expiry check complete: usersWithStaleCredentials={}, notified={}",
                rows.size(),
                notified);
    }
}
