package com.securevault.password;

import com.securevault.password.dto.PasswordStrengthResponse;

public interface PasswordStrengthService {

    /**
     * Deterministic — the same input always yields the same score (P3.1). Never logs, persists, or
     * audits the password.
     */
    PasswordStrengthResponse analyze(String password);

    /** Shared by PasswordStrengthResponse.strength() and vault-health band tallying (S3.3). */
    String labelForScore(int score);
}
