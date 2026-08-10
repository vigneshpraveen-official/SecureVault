package com.securevault.user.dto;

import com.securevault.user.Role;
import com.securevault.user.User;
import java.time.Instant;

/**
 * Never carries passwordHash — this is the only shape a User is allowed to leave the service layer
 * in.
 */
public record UserResponse(Long id, String fullName, String email, Role role, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt());
    }
}
