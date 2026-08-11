package com.securevault.security;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * P5.2/ADR-024: fail-OPEN by design. If Redis is unreachable, {@link #isDenylisted} returns false
 * (request proceeds as if not logged out) and {@link #denylist} silently no-ops after logging a
 * WARN, rather than letting a Redis outage take down every authenticated request in the app. The
 * accepted tradeoff: a logged-out access token could keep working until its own natural 15-minute
 * expiry if Redis happens to be down at that exact moment — bounded, short-lived exposure, traded
 * against total API unavailability on a fail-closed design. See ADR-024 for the full reasoning.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenDenylistServiceImpl implements TokenDenylistService {

    private static final String KEY_PREFIX = "jwt:denylist:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void denylist(String jti, Duration ttl) {
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + jti, "1", ttl);
        } catch (Exception ex) {
            log.warn(
                    "Redis unavailable — could not denylist jti (fail-open, ADR-024): {}",
                    ex.getMessage());
        }
    }

    @Override
    public boolean isDenylisted(String jti) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + jti));
        } catch (Exception ex) {
            log.warn(
                    "Redis unavailable — treating token as not denylisted (fail-open, ADR-024): {}",
                    ex.getMessage());
            return false;
        }
    }
}
