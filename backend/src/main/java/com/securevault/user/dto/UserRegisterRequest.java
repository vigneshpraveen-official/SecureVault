package com.securevault.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Full validation coverage — see docs/validation.md for the annotation-by-annotation why. */
public record UserRegisterRequest(
        @NotBlank @Size(max = 100, message = "must be at most 100 characters") String fullName,
        @NotBlank @Email @Size(max = 150, message = "must be at most 150 characters") String email,
        @NotBlank
                @Size(min = 8, max = 72, message = "must be between 8 and 72 characters")
                @Pattern(
                        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).+$",
                        message =
                                "must contain at least one uppercase letter, one lowercase"
                                        + " letter, one digit, and one special character")
                String password) {}
