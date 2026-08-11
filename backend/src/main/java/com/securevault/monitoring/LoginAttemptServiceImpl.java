package com.securevault.monitoring;

import com.securevault.monitoring.dto.LoginAttemptResponse;
import com.securevault.user.User;
import com.securevault.user.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * P5.5 step 2: lock after 5 consecutive failures within 15 minutes; auto-unlock after 30 minutes
 * (CustomUserDetailsService, derived from the latest failure timestamp — see ADR). Locked accounts
 * never get a distinguishable response from AuthController (generic InvalidCredentialsException,
 * same 401 as a wrong password) — confirming an account is locked would confirm it exists, the same
 * anti-enumeration reasoning as login already applies to wrong-password vs. unknown-email.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoginAttemptServiceImpl implements LoginAttemptService {

    private static final int MAX_CONSECUTIVE_FAILURES = 5;
    private static final int LOCKOUT_WINDOW_MINUTES = 15;
    private static final int ELEVATED_FAILURE_THRESHOLD = 3;

    private final LoginAttemptRepository loginAttemptRepository;
    private final UserRepository userRepository;
    private final SecurityAlertService securityAlertService;

    @Override
    @Transactional
    public void recordSuccess(String email, Long userId, String ip, String userAgent) {
        loginAttemptRepository.save(
                LoginAttempt.builder()
                        .email(email)
                        .successful(true)
                        .ipAddress(ip)
                        .userAgent(userAgent)
                        .attemptedAt(Instant.now())
                        .build());
        userRepository
                .findById(userId)
                .ifPresent(
                        user -> {
                            user.setFailedLoginAttempts(0);
                            user.setAccountLocked(false);
                            userRepository.save(user);
                        });
    }

    @Override
    @Transactional
    public void recordFailure(String email, String ip, String userAgent, String failureReason) {
        loginAttemptRepository.save(
                LoginAttempt.builder()
                        .email(email)
                        .successful(false)
                        .ipAddress(ip)
                        .userAgent(userAgent)
                        .attemptedAt(Instant.now())
                        .failureReason(failureReason)
                        .build());

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            // Nothing to lock — but the attempt above is still recorded, same anti-enumeration
            // reasoning as InvalidCredentialsException never distinguishing this case either.
            return;
        }

        long recentFailures =
                loginAttemptRepository.countByEmailAndSuccessfulFalseAndAttemptedAtAfter(
                        email, Instant.now().minus(LOCKOUT_WINDOW_MINUTES, ChronoUnit.MINUTES));
        user.setFailedLoginAttempts((int) recentFailures);

        if (recentFailures >= MAX_CONSECUTIVE_FAILURES) {
            user.setAccountLocked(true);
            userRepository.save(user);
            log.warn(
                    "Account locked after {} failed attempts within {} min: userId={}",
                    recentFailures,
                    LOCKOUT_WINDOW_MINUTES,
                    user.getId());
            securityAlertService.raise(
                    user.getId(),
                    AlertType.BRUTE_FORCE_LOCKOUT,
                    AlertSeverity.HIGH,
                    "Account locked after "
                            + recentFailures
                            + " failed login attempts within "
                            + LOCKOUT_WINDOW_MINUTES
                            + " minutes");
        } else {
            userRepository.save(user);
            if (recentFailures >= ELEVATED_FAILURE_THRESHOLD) {
                // Independently testable anomaly rule (P5.5 step 3), distinct from the lockout
                // itself — an early warning at 3, lockout still happens separately at 5.
                securityAlertService.raise(
                        user.getId(),
                        AlertType.ELEVATED_FAILED_ATTEMPTS,
                        AlertSeverity.MEDIUM,
                        recentFailures
                                + " failed login attempts within "
                                + LOCKOUT_WINDOW_MINUTES
                                + " minutes");
            }
        }
    }

    @Override
    public List<LoginAttemptResponse> listForUser(String email) {
        return loginAttemptRepository.findByEmailOrderByAttemptedAtDesc(email).stream()
                .map(LoginAttemptServiceImpl::toResponse)
                .toList();
    }

    @Override
    public List<LoginAttemptResponse> listAll() {
        return loginAttemptRepository.findAllByOrderByAttemptedAtDesc().stream()
                .map(LoginAttemptServiceImpl::toResponse)
                .toList();
    }

    private static LoginAttemptResponse toResponse(LoginAttempt attempt) {
        return new LoginAttemptResponse(
                attempt.getId(),
                attempt.getEmail(),
                attempt.isSuccessful(),
                attempt.getIpAddress(),
                attempt.getUserAgent(),
                attempt.getAttemptedAt(),
                attempt.getFailureReason());
    }
}
