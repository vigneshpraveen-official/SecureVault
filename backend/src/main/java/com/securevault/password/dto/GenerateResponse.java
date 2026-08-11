package com.securevault.password.dto;

public record GenerateResponse(String password, PasswordStrengthResponse strength) {}
