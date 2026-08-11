package com.securevault.user;

import com.securevault.user.dto.UserResponse;
import org.mapstruct.Mapper;

/**
 * Entity to Response DTO only — registration builds the User entity in the service because password
 * hashing (BCrypt) is business logic, not a mapping concern (P2.1/M-24).
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(User user);
}
