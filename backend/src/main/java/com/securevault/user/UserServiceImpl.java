package com.securevault.user;

import com.securevault.user.dto.UserRegisterRequest;
import com.securevault.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserResponse register(UserRegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException(request.email());
        }

        User user =
                User.builder()
                        .fullName(request.fullName())
                        .email(request.email())
                        .passwordHash(passwordEncoder.encode(request.password()))
                        .role(Role.USER)
                        .build();

        return UserResponse.from(userRepository.save(user));
    }
}
