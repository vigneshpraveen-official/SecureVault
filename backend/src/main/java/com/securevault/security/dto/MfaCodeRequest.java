package com.securevault.security.dto;

import jakarta.validation.constraints.NotBlank;

/** Shared by /mfa/verify and /mfa/disable — both just need "the current 6-digit code". */
public record MfaCodeRequest(@NotBlank String code) {}
