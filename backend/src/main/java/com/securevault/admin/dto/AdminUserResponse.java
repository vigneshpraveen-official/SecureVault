package com.securevault.admin.dto;

import com.securevault.user.Role;
import java.time.Instant;

public record AdminUserResponse(
        Long id,
        String fullName,
        String email,
        Role role,
        boolean accountLocked,
        boolean mfaEnabled,
        Instant createdAt) {}
