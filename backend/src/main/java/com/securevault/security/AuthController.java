package com.securevault.security;

import com.securevault.common.async.AsyncTaskService;
import com.securevault.common.exception.InvalidCredentialsException;
import com.securevault.common.exception.MfaInvalidException;
import com.securevault.common.response.ApiResponse;
import com.securevault.common.util.LogMasking;
import com.securevault.common.util.Sha256;
import com.securevault.monitoring.DeviceService;
import com.securevault.monitoring.LoginAttemptService;
import com.securevault.security.dto.LoginRequest;
import com.securevault.security.dto.LoginResponse;
import com.securevault.security.dto.LogoutRequest;
import com.securevault.security.dto.MfaChallengeRequest;
import com.securevault.security.dto.MfaCodeRequest;
import com.securevault.security.dto.MfaSetupResponse;
import com.securevault.security.dto.MfaVerifyResponse;
import com.securevault.security.dto.RefreshRequest;
import com.securevault.security.dto.TokenRefreshResponse;
import com.securevault.user.User;
import com.securevault.user.UserRepository;
import io.jsonwebtoken.JwtException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Auth", description = "Registration, login, refresh/logout, and MFA")
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final AsyncTaskService asyncTaskService;
    private final RefreshTokenService refreshTokenService;
    private final TokenDenylistService tokenDenylistService;
    private final MfaService mfaService;
    private final MfaChallengeService mfaChallengeService;
    private final DeviceService deviceService;
    private final LoginAttemptService loginAttemptService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");
        Authentication authentication;
        try {
            authentication =
                    authenticationManager.authenticate(
                            new UsernamePasswordAuthenticationToken(
                                    request.email(), request.password()));
        } catch (LockedException ex) {
            // Locked accounts get the exact same generic 401 as a wrong password (P5.5 step 2) —
            // confirming a lock would confirm the account exists, the same reasoning already
            // applied to unknown-email vs. wrong-password. Recorded as its own failure reason
            // internally only; never surfaced to the client.
            loginAttemptService.recordFailure(request.email(), ip, userAgent, "ACCOUNT_LOCKED");
            log.warn("Login attempt on locked account: {}", LogMasking.maskEmail(request.email()));
            throw new InvalidCredentialsException();
        } catch (BadCredentialsException | UsernameNotFoundException ex) {
            loginAttemptService.recordFailure(request.email(), ip, userAgent, "BAD_CREDENTIALS");
            // WARN, masked email (P4.7/M-47) — a failed login is a recoverable/suspicious
            // condition worth a line of its own, distinct from GlobalExceptionHandler's generic
            // BusinessException WARN, which never sees the attempted email at all.
            log.warn("Login failed for {}", LogMasking.maskEmail(request.email()));
            throw new InvalidCredentialsException();
        }

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user =
                userRepository
                        .findByEmail(principal.getUsername())
                        .orElseThrow(InvalidCredentialsException::new);
        // "successful" here means the PASSWORD was correct — brute-force protection is about
        // guessing the password, so the failure counter resets the moment that's proven, even if
        // an MFA challenge is still pending below. Full login completion (including a passed MFA
        // challenge) has nothing further to add to lockout/brute-force tracking.
        loginAttemptService.recordSuccess(user.getEmail(), user.getId(), ip, userAgent);

        if (user.isMfaEnabled()) {
            // Correct password, but no tokens yet (P5.4 step 2) — the real access/refresh pair
            // is only issued once POST /api/auth/mfa/challenge accepts a code against this token.
            String challengeToken = mfaChallengeService.createChallenge(user.getId());
            log.info("Login password verified, MFA challenge issued: userId={}", user.getId());
            LoginResponse response =
                    new LoginResponse(
                            true,
                            challengeToken,
                            null,
                            null,
                            user.getId(),
                            user.getFullName(),
                            user.getEmail(),
                            user.getRole());
            return ResponseEntity.ok(ApiResponse.success("MFA verification required", response));
        }

        return completeLogin(user, httpRequest);
    }

    @PostMapping("/mfa/challenge")
    public ResponseEntity<ApiResponse<LoginResponse>> mfaChallenge(
            @Valid @RequestBody MfaChallengeRequest request, HttpServletRequest httpRequest) {
        Long userId = mfaChallengeService.peekChallenge(request.challengeToken());
        User user = userRepository.findById(userId).orElseThrow(InvalidCredentialsException::new);
        if (!mfaService.verifyLoginCode(user, request.code())) {
            throw new MfaInvalidException();
        }
        // Only invalidated on success (P5.4) — a wrong code must not burn the challenge token,
        // or a single mistyped digit would force the user to restart the whole login from
        // scratch instead of just retrying within the 2-minute window.
        mfaChallengeService.invalidateChallenge(request.challengeToken());
        return completeLogin(user, httpRequest);
    }

    private ResponseEntity<ApiResponse<LoginResponse>> completeLogin(
            User user, HttpServletRequest httpRequest) {
        String fingerprint = deviceFingerprint(httpRequest);
        TokenRefreshResponse tokens = refreshTokenService.issue(user.getId(), fingerprint);
        boolean newDevice =
                deviceService.recordLogin(
                        user.getId(),
                        fingerprint,
                        deviceName(httpRequest),
                        httpRequest.getRemoteAddr(),
                        httpRequest.getHeader("User-Agent"));

        log.info("Login succeeded: userId={}, newDevice={}", user.getId(), newDevice);
        // Best-effort, informational, off the request thread (P4.6) — contrast with
        // CredentialServiceImpl's AuditService.record(...), which stays synchronous because it
        // must roll back with its business write. A login activity line has nothing to roll
        // back with; login already succeeded by the time this runs.
        asyncTaskService.logActivity("User logged in: userId=" + user.getId());

        LoginResponse response =
                new LoginResponse(
                        false,
                        null,
                        tokens.accessToken(),
                        tokens.refreshToken(),
                        user.getId(),
                        user.getFullName(),
                        user.getEmail(),
                        user.getRole());
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    // SHA-256 of User-Agent + remote IP — an approximation, not a hardened fingerprint (no
    // client-side entropy source in scope for this project), good enough to distinguish "same
    // browser on the same network" from "somewhere/something else" for P5.4's device list.
    private String deviceFingerprint(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        String ip = request.getRemoteAddr();
        return Sha256.hex((ua == null ? "unknown" : ua) + "|" + (ip == null ? "unknown" : ip));
    }

    private String deviceName(HttpServletRequest request) {
        String custom = request.getHeader("X-Device-Name");
        return (custom == null || custom.isBlank()) ? "Unknown device" : custom;
    }

    // Deliberately public (SecurityConfig's explicit permitAll list) — a client calling this has,
    // by definition, no valid access token left to authenticate with; the refresh token itself is
    // the credential (P5.2).
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refresh(
            @Valid @RequestBody RefreshRequest request) {
        TokenRefreshResponse tokens = refreshTokenService.refresh(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.success("Token refreshed", tokens));
    }

    // Reads the Authorization header directly rather than @AuthenticationPrincipal — logout must
    // still denylist a token whose signature is valid even in the (rare) case the filter chain
    // left the SecurityContext unauthenticated for some other reason, and it must work
    // uniformly whether or not the endpoint requires authentication (P5.2 step 4).
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest httpRequest, @Valid @RequestBody LogoutRequest request) {
        refreshTokenService.revoke(request.refreshToken());

        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String accessToken = authHeader.substring(BEARER_PREFIX.length());
            try {
                String jti = jwtService.extractJti(accessToken);
                Duration remaining =
                        Duration.between(
                                Instant.now(),
                                jwtService.extractExpiration(accessToken).toInstant());
                tokenDenylistService.denylist(jti, remaining);
            } catch (JwtException | IllegalArgumentException ex) {
                // Already malformed/expired — nothing left to protect by denylisting it.
                log.debug("Logout: access token not denylisted (already invalid/expired)");
            }
        }

        log.info("Logout completed");
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));
    }

    // Authenticated (SecurityConfig requires a valid access token for everything under
    // /api/auth/mfa/** except /mfa/challenge) — @AuthenticationPrincipal is only safe to
    // dereference here because permitAll does NOT cover these three routes.
    @PostMapping("/mfa/setup")
    public ResponseEntity<ApiResponse<MfaSetupResponse>> mfaSetup(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(
                ApiResponse.success("MFA setup initiated", mfaService.setup(principal.getId())));
    }

    @PostMapping("/mfa/verify")
    public ResponseEntity<ApiResponse<MfaVerifyResponse>> mfaVerify(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody MfaCodeRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "MFA enabled", mfaService.verify(principal.getId(), request.code())));
    }

    @PostMapping("/mfa/disable")
    public ResponseEntity<ApiResponse<Void>> mfaDisable(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody MfaCodeRequest request) {
        mfaService.disable(principal.getId(), request.code());
        return ResponseEntity.ok(ApiResponse.success("MFA disabled", null));
    }
}
