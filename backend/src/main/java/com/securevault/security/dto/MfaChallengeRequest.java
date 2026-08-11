package com.securevault.security.dto;

import jakarta.validation.constraints.NotBlank;

public record MfaChallengeRequest(@NotBlank String challengeToken, @NotBlank String code) {}
