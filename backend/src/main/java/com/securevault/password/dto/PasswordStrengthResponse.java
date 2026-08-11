package com.securevault.password.dto;

import java.util.List;

/**
 * score: 0-5 (see docs/password-policy.md for the exact algorithm). strength: the score's label
 * (Very Weak/Weak/Medium/Strong/Very Strong). entropyBits: Shannon entropy of the password's own
 * character distribution, scaled by length. feedback: actionable, specific reasons — never "make it
 * stronger".
 */
public record PasswordStrengthResponse(
        int score, String strength, double entropyBits, List<String> feedback) {}
