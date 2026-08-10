package com.securevault.security;

import com.securevault.common.response.ApiResponse;
import com.securevault.security.dto.LoginRequest;
import com.securevault.security.dto.LoginResponse;
import com.securevault.user.User;
import com.securevault.user.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;

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
            throw new InvalidCredentialsException();
        }

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        String token = jwtService.generateAccessToken(principal);
        User user =
                userRepository
                        .findByEmail(principal.getUsername())
                        .orElseThrow(InvalidCredentialsException::new);

        LoginResponse response =
                new LoginResponse(
                        token, user.getId(), user.getFullName(), user.getEmail(), user.getRole());
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    // Local handler for this session only — full GlobalExceptionHandler + ErrorCode enum
    // arrive in S2.3 (M-26/M-27). TODO(S2.3): move this there.
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidCredentials(
            InvalidCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ex.getMessage(), "INVALID_CREDENTIALS", null));
    }
}
