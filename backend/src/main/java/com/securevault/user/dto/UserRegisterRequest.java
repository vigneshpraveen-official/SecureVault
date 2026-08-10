package com.securevault.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Minimal validation added in S1.6 to close a Milestone 1 quality gap. Full coverage is S2.2
 * (M-25).
 */
public record UserRegisterRequest(
        @NotBlank String fullName,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, message = "must be at least 8 characters") String password) {}
