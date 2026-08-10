package com.securevault.security.dto;

import com.securevault.user.Role;

public record LoginResponse(
        String accessToken, Long userId, String fullName, String email, Role role) {}
