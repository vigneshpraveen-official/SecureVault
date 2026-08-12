package com.securevault.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.securevault.common.async.AsyncTaskService;
import com.securevault.common.exception.DuplicateEmailException;
import com.securevault.user.dto.UserRegisterRequest;
import com.securevault.user.dto.UserResponse;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UserMapper userMapper;
    @Mock private AsyncTaskService asyncTaskService;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService =
                new UserServiceImpl(userRepository, passwordEncoder, userMapper, asyncTaskService);
    }

    @Test
    void should_rejectRegistration_when_emailAlreadyExists() {
        UserRegisterRequest request =
                new UserRegisterRequest("Dave", "dave@example.com", "Str0ng!Pass");
        when(userRepository.existsByEmail("dave@example.com")).thenReturn(true);

        assertThrows(DuplicateEmailException.class, () -> userService.register(request));

        verify(userRepository, never()).save(any());
    }

    @Test
    void should_hashThePasswordBeforePersisting_when_registering() {
        UserRegisterRequest request =
                new UserRegisterRequest("Dave", "dave@example.com", "Str0ng!Pass");
        when(userRepository.existsByEmail("dave@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Str0ng!Pass")).thenReturn("$2a$10$hashed");
        when(userRepository.save(any(User.class)))
                .thenAnswer(
                        invocation -> {
                            User u = invocation.getArgument(0);
                            u.setId(1L);
                            return u;
                        });
        when(userMapper.toResponse(any(User.class)))
                .thenReturn(
                        new UserResponse(1L, "Dave", "dave@example.com", Role.USER, Instant.now()));

        userService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("$2a$10$hashed", captor.getValue().getPasswordHash());
    }

    @Test
    void should_neverExposeThePasswordHash_when_returningTheResponse() {
        UserRegisterRequest request =
                new UserRegisterRequest("Dave", "dave@example.com", "Str0ng!Pass");
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashed");
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        UserResponse expected =
                new UserResponse(1L, "Dave", "dave@example.com", Role.USER, Instant.now());
        when(userMapper.toResponse(any(User.class))).thenReturn(expected);

        UserResponse actual = userService.register(request);

        // UserResponse has no passwordHash field at all — the compiler already guarantees this,
        // but the point of this test is that the service returns exactly what the mapper produced,
        // never the raw entity.
        assertEquals(expected, actual);
    }

    @Test
    void should_defaultRoleToUser_when_registering() {
        UserRegisterRequest request =
                new UserRegisterRequest("Dave", "dave@example.com", "Str0ng!Pass");
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashed");
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toResponse(any(User.class)))
                .thenReturn(
                        new UserResponse(1L, "Dave", "dave@example.com", Role.USER, Instant.now()));

        userService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals(Role.USER, captor.getValue().getRole());
    }

    @Test
    void should_sendTheWelcomeEmailOffTheRequestThread_when_registrationSucceeds() {
        UserRegisterRequest request =
                new UserRegisterRequest("Dave", "dave@example.com", "Str0ng!Pass");
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashed");
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toResponse(any(User.class)))
                .thenReturn(
                        new UserResponse(1L, "Dave", "dave@example.com", Role.USER, Instant.now()));

        userService.register(request);

        // The interaction IS the requirement here (P4.6's async-dispatch boundary) — asserting it
        // was called is the correct thing to verify, not an internal implementation detail.
        verify(asyncTaskService)
                .sendNotificationEmail(eq("dave@example.com"), anyString(), anyString());
    }

    @Test
    void should_notPersistAnything_when_emailAlreadyExists() {
        UserRegisterRequest request =
                new UserRegisterRequest("Dave", "dave@example.com", "Str0ng!Pass");
        when(userRepository.existsByEmail("dave@example.com")).thenReturn(true);

        assertThrows(DuplicateEmailException.class, () -> userService.register(request));

        verify(asyncTaskService, never())
                .sendNotificationEmail(anyString(), anyString(), anyString());
    }
}
