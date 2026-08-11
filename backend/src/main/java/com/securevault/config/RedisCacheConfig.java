package com.securevault.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.util.Map;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * P5.3/D-13: Spring Cache backed by Redis. Per-cache TTLs and a shared key prefix (`sv:cache:`,
 * distinct from the `jwt:denylist:` prefix TokenDenylistServiceImpl uses) so cache entries and the
 * logout denylist never collide in the same Redis instance. Null values are never cached
 * (disableCachingNullValues) — a cache miss and a "confirmed empty" result must stay
 * distinguishable, and caching nulls would let a since-fixed 404/empty-list linger.
 *
 * <p>What is deliberately NEVER a cache: decrypted credentials, tokens, or MFA secrets (P5.3 step
 * 3) — nothing in this config, or anywhere else in the codebase, puts any of those in Redis.
 */
@Configuration
@EnableCaching
public class RedisCacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        // A dedicated ObjectMapper, not the app-wide REST one (P5.3):
        // GenericJackson2JsonRedisSerializer needs default typing (embeds "@class" per value) to
        // deserialize generics like PagedResponse<CredentialSummaryResponse> back to their
        // concrete type instead of a LinkedHashMap — found live, the first real cache-hit read
        // 500'd with a ClassCastException until this was added. Activating default typing on the
        // shared REST mapper instead would leak "@class" into every JSON HTTP response, so this
        // stays its own instance.
        // DefaultTyping.EVERYTHING, not NON_FINAL (found live, second bug): every cached DTO here
        // is a Java record, which is implicitly `final` — NON_FINAL skips embedding "@class" for
        // final types, so PagedResponse's own top-level type resolved fine but each
        // CredentialSummaryResponse inside its generically-typed `content: List<T>` field came
        // back type-erased to Object with no type id, and deserialization 500'd with
        // "InvalidTypeIdException: missing type id". EVERYTHING guarantees a type id on every
        // value, records included.
        ObjectMapper cacheObjectMapper =
                new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .activateDefaultTyping(
                                LaissezFaireSubTypeValidator.instance,
                                ObjectMapper.DefaultTyping.EVERYTHING,
                                JsonTypeInfo.As.PROPERTY);

        RedisCacheConfiguration base =
                RedisCacheConfiguration.defaultCacheConfig()
                        .disableCachingNullValues()
                        .computePrefixWith(cacheName -> "sv:cache:" + cacheName + "::")
                        .serializeKeysWith(
                                RedisSerializationContext.SerializationPair.fromSerializer(
                                        new StringRedisSerializer()))
                        .serializeValuesWith(
                                RedisSerializationContext.SerializationPair.fromSerializer(
                                        new GenericJackson2JsonRedisSerializer(cacheObjectMapper)));

        // vaultList: invalidated on any create/update/delete/restore for the affected user
        // (CredentialServiceImpl's @CacheEvict) — 5 min is a safety net for whatever a mutation
        // might miss, not the primary correctness mechanism.
        // passwordStrength: keyed by Sha256.hex(password), NEVER the password itself
        // (PasswordStrengthServiceImpl) — short TTL since it's a pure, deterministic function
        // and a stale hit is indistinguishable from a fresh one.
        // dashboard: aggregates recomputed from live data (S5.7) — short TTL bounds the
        // documented staleness window rather than eliminating it.
        Map<String, RedisCacheConfiguration> perCache =
                Map.of(
                        "vaultList", base.entryTtl(Duration.ofMinutes(5)),
                        "passwordStrength", base.entryTtl(Duration.ofMinutes(10)),
                        "dashboard", base.entryTtl(Duration.ofMinutes(2)));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(base)
                .withInitialCacheConfigurations(perCache)
                .build();
    }
}
