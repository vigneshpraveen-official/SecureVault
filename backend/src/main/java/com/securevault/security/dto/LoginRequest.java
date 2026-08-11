package com.securevault.security.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Presence-only validation (P2.2/M-25) — deliberately no @Pattern/@Size complexity check on
 * password: login must keep authenticating users whose password predates any later policy
 * tightening. Full coverage rationale in docs/validation.md.
 */
public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
