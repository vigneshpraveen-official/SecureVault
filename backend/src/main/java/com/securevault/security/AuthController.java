package com.securevault.security;

import com.securevault.common.async.AsyncTaskService;
import com.securevault.common.exception.InvalidCredentialsException;
import com.securevault.common.response.ApiResponse;
import com.securevault.common.util.LogMasking;
import com.securevault.security.dto.LoginRequest;
import com.securevault.security.dto.LoginResponse;
import com.securevault.user.User;
import com.securevault.user.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final AsyncTaskService asyncTaskService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        Authentication authentication;
        try {
            authentication =
                    authenticationManager.authenticate(
                            new UsernamePasswordAuthenticationToken(
                                    request.email(), request.password()));
        } catch (BadCredentialsException | UsernameNotFoundException ex) {
            // WARN, masked email (P4.7/M-47) — a failed login is a recoverable/suspicious
            // condition worth a line of its own, distinct from GlobalExceptionHandler's generic
            // BusinessException WARN, which never sees the attempted email at all.
            log.warn("Login failed for {}", LogMasking.maskEmail(request.email()));
            throw new InvalidCredentialsException();
        }

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        String token = jwtService.generateAccessToken(principal);
        User user =
                userRepository
                        .findByEmail(principal.getUsername())
                        .orElseThrow(InvalidCredentialsException::new);

        log.info("Login succeeded: userId={}", user.getId());
        // Best-effort, informational, off the request thread (P4.6) — contrast with
        // CredentialServiceImpl's AuditService.record(...), which stays synchronous because it
        // must roll back with its business write. A login activity line has nothing to roll
        // back with; login already succeeded by the time this runs.
        asyncTaskService.logActivity("User logged in: userId=" + user.getId());

        LoginResponse response =
                new LoginResponse(
                        token, user.getId(), user.getFullName(), user.getEmail(), user.getRole());
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }
}
