package com.securevault.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Single shared SHA-256 hex implementation (P5.3) — previously duplicated in CredentialServiceImpl
 * (reused-password detection) and TokenHasher (refresh token hashing); a third caller (the
 * password-strength cache key, keyed by hash, never by the password itself) made the duplication
 * concrete enough to consolidate.
 */
public final class Sha256 {

    private Sha256() {}

    public static String hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
