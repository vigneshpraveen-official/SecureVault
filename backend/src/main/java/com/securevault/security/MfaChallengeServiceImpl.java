package com.securevault.security;

import com.securevault.common.exception.MfaInvalidException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis-backed, same key-prefix-per-purpose convention as TokenDenylistServiceImpl
 * (`jwt:denylist:`) — this uses `mfa:challenge:`. Deliberately NOT fail-open like the denylist: if
 * Redis is unavailable, MFA login must fail closed (no challenge issuable, no challenge consumable)
 * rather than silently letting a second factor be skipped.
 */
@Service
@RequiredArgsConstructor
public class MfaChallengeServiceImpl implements MfaChallengeService {

    private static final String KEY_PREFIX = "mfa:challenge:";
    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(2);

    private final StringRedisTemplate redisTemplate;

    @Override
    public String createChallenge(Long userId) {
        String token = TokenHasher.generateRawToken();
        redisTemplate.opsForValue().set(KEY_PREFIX + token, userId.toString(), CHALLENGE_TTL);
        return token;
    }

    @Override
    public Long peekChallenge(String challengeToken) {
        String userIdValue = redisTemplate.opsForValue().get(KEY_PREFIX + challengeToken);
        if (userIdValue == null) {
            throw new MfaInvalidException();
        }
        return Long.parseLong(userIdValue);
    }

    @Override
    public void invalidateChallenge(String challengeToken) {
        redisTemplate.delete(KEY_PREFIX + challengeToken);
    }
}
