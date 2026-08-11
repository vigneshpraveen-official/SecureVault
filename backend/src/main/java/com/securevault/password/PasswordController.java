package com.securevault.password;

import com.securevault.common.response.ApiResponse;
import com.securevault.password.dto.GenerateRequest;
import com.securevault.password.dto.GenerateResponse;
import com.securevault.password.dto.PasswordStrengthRequest;
import com.securevault.password.dto.PasswordStrengthResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public (permitAll in SecurityConfig, like /api/auth/**) — strength/generation are stateless
 * utilities a client may need before a JWT exists yet (e.g. live feedback on a registration form),
 * and neither touches user or vault data.
 */
@RestController
@RequestMapping("/api/password")
@RequiredArgsConstructor
public class PasswordController {

    private final PasswordStrengthService passwordStrengthService;
    private final PasswordGeneratorService passwordGeneratorService;

    @PostMapping("/strength")
    public ResponseEntity<ApiResponse<PasswordStrengthResponse>> strength(
            @Valid @RequestBody PasswordStrengthRequest request) {
        PasswordStrengthResponse response = passwordStrengthService.analyze(request.password());
        return ResponseEntity.ok(ApiResponse.success("Password analyzed", response));
    }

    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<GenerateResponse>> generate(
            @Valid @RequestBody GenerateRequest request) {
        GenerateResponse response = passwordGeneratorService.generate(request);
        return ResponseEntity.ok(ApiResponse.success("Password generated", response));
    }
}
