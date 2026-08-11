package com.securevault.user.dto;

import com.securevault.user.Role;
import java.time.Instant;

/**
 * Never carries passwordHash — this is the only shape a User is allowed to leave the service layer
 * in. Built from the entity by UserMapper (MapStruct), not a static factory (P2.1/M-24).
 */
public record UserResponse(Long id, String fullName, String email, Role role, Instant createdAt) {}
