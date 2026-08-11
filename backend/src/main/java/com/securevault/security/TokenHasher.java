package com.securevault.security;

import com.securevault.common.util.Sha256;
import java.security.SecureRandom;
import java.util.Base64;

/** SHA-256 hashing for refresh tokens (never stored raw) + SecureRandom raw token generation. */
public final class TokenHasher {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private TokenHasher() {}

    public static String generateRawToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256Hex(String raw) {
        return Sha256.hex(raw);
    }
}
