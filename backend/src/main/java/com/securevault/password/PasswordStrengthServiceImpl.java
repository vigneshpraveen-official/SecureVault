package com.securevault.password;

import com.securevault.password.dto.PasswordStrengthResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Exact algorithm documented in docs/password-policy.md — this class must stay in lockstep with
 * that document (P3.1/M-29: "reproducible and explainable in review"). Never logs, persists, or
 * audits the submitted password — it never leaves this method as anything but a score.
 */
@Service
public class PasswordStrengthServiceImpl implements PasswordStrengthService {

    private static final int REPEAT_MIN_RUN = 3;
    private static final int SEQUENCE_MIN_RUN = 4;
    private static final List<String> KEYBOARD_ROWS =
            List.of("qwertyuiop", "asdfghjkl", "zxcvbnm", "1234567890");

    private final Set<String> commonPasswords;

    public PasswordStrengthServiceImpl() {
        this.commonPasswords = loadCommonPasswords();
    }

    private Set<String> loadCommonPasswords() {
        try (InputStream in = getClass().getResourceAsStream("/password/common-passwords.txt")) {
            if (in == null) {
                throw new IllegalStateException("password/common-passwords.txt not on classpath");
            }
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return reader.lines()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .map(line -> line.toLowerCase())
                        .collect(Collectors.toUnmodifiableSet());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load common-passwords.txt", e);
        }
    }

    // P5.3: keyed by a SHA-256 hash of the password (com.securevault.common.util.Sha256), NEVER
    // by or storing the password itself — the cached value is only the deterministic analysis
    // result (score/strength/entropy/feedback), already no more sensitive than the strengthScore
    // column CredentialServiceImpl persists to Postgres in plaintext-adjacent form today. Short
    // TTL (RedisCacheConfig) since a stale hit is indistinguishable from a fresh one for a pure
    // function of the input.
    @Override
    @Cacheable(
            cacheNames = "passwordStrength",
            key = "T(com.securevault.common.util.Sha256).hex(#password)")
    public PasswordStrengthResponse analyze(String password) {
        List<String> feedback = new ArrayList<>();
        int score = 0;

        boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLower = password.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSpecial =
                password.chars()
                        .anyMatch(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c));

        // Length: thresholds at 8/12/16 shape the *feedback* granularity; only the >12 check
        // contributes to the score, per the mentor's explicit baseline formula.
        int length = password.length();
        if (length > 12) {
            score++;
        }
        if (length < 8) {
            feedback.add("Increase length to at least 8 characters");
        } else if (length < 12) {
            feedback.add("Increase length to 12+ characters");
        } else if (length < 16) {
            feedback.add("Increase length to 16+ characters for extra safety margin");
        }

        if (hasUpper) {
            score++;
        } else {
            feedback.add("Add an uppercase letter");
        }
        if (hasLower) {
            score++;
        } else {
            feedback.add("Add a lowercase letter");
        }
        if (hasDigit) {
            score++;
        } else {
            feedback.add("Add a number");
        }
        if (hasSpecial) {
            score++;
        } else {
            feedback.add("Add a special character");
        }

        if (hasConsecutiveRepeats(password)) {
            score--;
            feedback.add(
                    "Avoid repeating the same character multiple times in a row (e.g. aaa, 111)");
        }
        if (hasSequentialPattern(password)) {
            score--;
            feedback.add("Avoid sequential patterns like 1234, abcd, or qwerty");
        }
        if (commonPasswords.contains(password.toLowerCase())) {
            score--;
            feedback.add(
                    "This is one of the most commonly used passwords — choose something unique");
        }

        score = Math.max(0, Math.min(5, score));
        double entropyBits = Math.round(shannonEntropyBits(password) * 10.0) / 10.0;

        return new PasswordStrengthResponse(
                score, labelForScore(score), entropyBits, List.copyOf(feedback));
    }

    @Override
    public String labelForScore(int score) {
        return switch (score) {
            case 0 -> "Very Weak";
            case 1, 2 -> "Weak";
            case 3 -> "Medium";
            case 4 -> "Strong";
            default -> "Very Strong";
        };
    }

    private boolean hasConsecutiveRepeats(String password) {
        int run = 1;
        for (int i = 1; i < password.length(); i++) {
            run = password.charAt(i) == password.charAt(i - 1) ? run + 1 : 1;
            if (run >= REPEAT_MIN_RUN) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSequentialPattern(String password) {
        String lower = password.toLowerCase();
        return hasAsciiRun(lower) || hasKeyboardRun(lower);
    }

    private boolean hasAsciiRun(String s) {
        int ascending = 1;
        int descending = 1;
        for (int i = 1; i < s.length(); i++) {
            char prev = s.charAt(i - 1);
            char cur = s.charAt(i);
            ascending = cur == prev + 1 ? ascending + 1 : 1;
            descending = cur == prev - 1 ? descending + 1 : 1;
            if (ascending >= SEQUENCE_MIN_RUN || descending >= SEQUENCE_MIN_RUN) {
                return true;
            }
        }
        return false;
    }

    private boolean hasKeyboardRun(String s) {
        for (String row : KEYBOARD_ROWS) {
            String reversed = new StringBuilder(row).reverse().toString();
            for (int i = 0; i + SEQUENCE_MIN_RUN <= s.length(); i++) {
                String window = s.substring(i, i + SEQUENCE_MIN_RUN);
                if (row.contains(window) || reversed.contains(window)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * True Shannon entropy of the password's own character-frequency distribution (H = -Σ
     * p(c)·log2(p(c))), scaled by length to give a total-information-content figure in bits — not a
     * charset-pool estimate. Documented in docs/password-policy.md.
     */
    private double shannonEntropyBits(String password) {
        int length = password.length();
        if (length == 0) {
            return 0.0;
        }
        Map<Character, Integer> frequencies = new HashMap<>();
        for (char c : password.toCharArray()) {
            frequencies.merge(c, 1, Integer::sum);
        }
        double entropyPerChar = 0.0;
        for (int count : frequencies.values()) {
            double p = (double) count / length;
            entropyPerChar -= p * (Math.log(p) / Math.log(2));
        }
        return entropyPerChar * length;
    }
}
