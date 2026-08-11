package com.securevault.user;

import com.securevault.common.async.AsyncTaskService;
import com.securevault.common.exception.DuplicateEmailException;
import com.securevault.user.dto.UserRegisterRequest;
import com.securevault.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final AsyncTaskService asyncTaskService;

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

        User saved = userRepository.save(user);
        log.info("User registered: userId={}", saved.getId());
        // Off the request thread on purpose (P4.6) — a slow/failed simulated email must never
        // delay or fail the registration response. No data is re-read from the DB here, so this
        // is safe even though @Async can fire before this @Transactional method commits.
        asyncTaskService.sendNotificationEmail(
                saved.getEmail(),
                "Welcome to SecureVault",
                "Your account has been created. You can now log in.");

        return userMapper.toResponse(saved);
    }
}
