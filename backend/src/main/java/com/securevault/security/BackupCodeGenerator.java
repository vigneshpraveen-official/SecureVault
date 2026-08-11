package com.securevault.security;

import java.security.SecureRandom;

/** SecureRandom only (master §9 — java.util.Random is banned for anything security-related). */
final class BackupCodeGenerator {

    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"; // no O/0/I/1/L
    private static final int GROUP_LEN = 4;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private BackupCodeGenerator() {}

    /**
     * e.g. "XK7P-9QRT" — 8 random characters from an ambiguity-free alphabet, grouped for
     * readability.
     */
    static String generate() {
        StringBuilder code = new StringBuilder(GROUP_LEN * 2 + 1);
        for (int i = 0; i < GROUP_LEN * 2; i++) {
            if (i == GROUP_LEN) {
                code.append('-');
            }
            code.append(ALPHABET.charAt(SECURE_RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
