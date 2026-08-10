package com.securevault.user;

import com.securevault.user.dto.UserRegisterRequest;
import com.securevault.user.dto.UserResponse;

public interface UserService {

    UserResponse register(UserRegisterRequest request);
}
