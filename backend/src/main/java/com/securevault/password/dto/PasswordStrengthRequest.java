package com.securevault.password.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Deliberately no other constraints (no @Size/@Pattern) — the whole point of this endpoint is to
 * score a password's weaknesses, so it must accept exactly what the mentor's own test cases expect
 * it to reject-by-score, not reject-by-validation (e.g. "password" must reach the service to be
 * scored Very Weak, not bounce as a 400).
 */
public record PasswordStrengthRequest(@NotBlank String password) {}
