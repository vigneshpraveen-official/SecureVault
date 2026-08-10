package com.securevault.security.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Minimal validation added in S1.6 to close a Milestone 1 quality gap. Full coverage is S2.2
 * (M-25).
 */
public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
