package com.securevault.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jsonwebtoken.ExpiredJwtException;
import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * P7.3's "expired token -> 401" row is proved here rather than live: the real access-token expiry
 * is 15 minutes (JWT_ACCESS_EXPIRY_MS), and waiting that long for a curl-based proof would be
 * neither fast nor deterministic. A JwtService instance built with a negative expiry generates an
 * already-expired token via the exact same public generateAccessToken() path production code uses —
 * no reflection, no private access.
 *
 * <p>jjwt's parser throws {@link ExpiredJwtException} the moment it parses an expired token's
 * claims — it never returns them for {@code isTokenExpired}'s own comparison to run. That exception
 * IS the expiry signal in production: {@code JwtAuthenticationFilter} catches {@code JwtException}
 * (its supertype) around the whole parse-and-validate call and leaves the request unauthenticated,
 * exactly like a malformed or tampered token (verified live — docs/evidence/security-matrix.md,
 * Section B). {@code isTokenExpired}'s internal {@code .before(new Date())} check only ever runs
 * for a token whose claims parsed successfully, i.e. one that was NOT yet expired at parse time.
 */
class JwtServiceTest {

    private static final UserDetails USER =
            User.withUsername("expiry-test@example.com")
                    .password("unused")
                    .authorities("ROLE_USER")
                    .build();

    @Test
    void should_throwExpiredJwtException_when_theTokenWasIssuedWithANegativeExpiryWindow() {
        JwtService jwtService = new JwtService(randomBase64Secret(), -1_000L);

        String alreadyExpiredToken = jwtService.generateAccessToken(USER);

        assertThrows(
                ExpiredJwtException.class, () -> jwtService.isTokenExpired(alreadyExpiredToken));
        assertThrows(
                ExpiredJwtException.class,
                () -> jwtService.isTokenValid(alreadyExpiredToken, USER),
                "isTokenValid must not silently swallow an expired token into `false` — the "
                        + "exception is what JwtAuthenticationFilter actually catches");
    }

    @Test
    void should_reportNotExpired_when_theTokenWasIssuedWithTheRealExpiryWindow() {
        JwtService jwtService = new JwtService(randomBase64Secret(), 900_000L);

        String freshToken = jwtService.generateAccessToken(USER);

        assertFalse(jwtService.isTokenExpired(freshToken));
        assertTrue(jwtService.isTokenValid(freshToken, USER));
    }

    private static String randomBase64Secret() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
