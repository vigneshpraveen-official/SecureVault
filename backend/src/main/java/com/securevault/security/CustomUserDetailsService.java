package com.securevault.security;

import com.securevault.monitoring.LoginAttemptRepository;
import com.securevault.user.User;
import com.securevault.user.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * P5.5: also owns the 30-minute auto-unlock (no dedicated "locked_at" column — derived from
 * login_attempts' most recent failure for this email, master §10 doesn't have that column and this
 * session doesn't own adding one via ADR for something derivable). Runs on every login attempt,
 * locked or not — the check itself is cheap (one indexed MAX query) and keeps the unlock logic in
 * exactly one place, upstream of both the password check and every other caller of
 * loadUserByUsername.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private static final int LOCK_DURATION_MINUTES = 30;

    private final UserRepository userRepository;
    private final LoginAttemptRepository loginAttemptRepository;

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user =
                userRepository
                        .findByEmail(email)
                        .orElseThrow(
                                () ->
                                        new UsernameNotFoundException(
                                                "No user with email: " + email));

        if (user.isAccountLocked()) {
            Optional<Instant> lastFailure = loginAttemptRepository.findLatestFailureTime(email);
            boolean staleEnoughToUnlock =
                    lastFailure
                            .map(
                                    t ->
                                            t.isBefore(
                                                    Instant.now()
                                                            .minus(
                                                                    LOCK_DURATION_MINUTES,
                                                                    ChronoUnit.MINUTES)))
                            .orElse(true);
            if (staleEnoughToUnlock) {
                user.setAccountLocked(false);
                user.setFailedLoginAttempts(0);
                userRepository.save(user);
            }
        }

        return new UserPrincipal(user);
    }
}
