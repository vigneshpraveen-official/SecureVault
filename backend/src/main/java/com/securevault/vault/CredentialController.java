package com.securevault.vault;

import com.securevault.common.response.ApiResponse;
import com.securevault.security.UserPrincipal;
import com.securevault.vault.dto.CredentialCreateRequest;
import com.securevault.vault.dto.CredentialDetailResponse;
import com.securevault.vault.dto.CredentialResponse;
import com.securevault.vault.dto.CredentialUpdateRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vault")
@RequiredArgsConstructor
public class CredentialController {

    private final CredentialService credentialService;

    @PostMapping
    public ResponseEntity<ApiResponse<CredentialResponse>> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CredentialCreateRequest request) {
        CredentialResponse response = credentialService.create(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Credential created successfully", response));
    }

    @GetMapping("/{id}")
    public ApiResponse<CredentialDetailResponse> getById(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return ApiResponse.success(
                "Credential retrieved successfully",
                credentialService.getByIdForUser(id, principal.getId()));
    }

    @GetMapping
    public ApiResponse<List<CredentialResponse>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) Category category) {
        return ApiResponse.success(
                "Vault retrieved successfully",
                credentialService.listForUser(principal.getId(), category));
    }

    @GetMapping("/search")
    public ApiResponse<List<CredentialResponse>> search(
            @AuthenticationPrincipal UserPrincipal principal, @RequestParam String q) {
        return ApiResponse.success(
                "Search results", credentialService.search(principal.getId(), q));
    }

    @PutMapping("/{id}")
    public ApiResponse<CredentialResponse> update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody CredentialUpdateRequest request) {
        return ApiResponse.success(
                "Credential updated successfully",
                credentialService.update(id, principal.getId(), request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        credentialService.delete(id, principal.getId());
        return ResponseEntity.noContent().build();
    }

    // Local handlers for this session only — full GlobalExceptionHandler + ErrorCode enum
    // arrive in S2.3 (M-26/M-27). TODO(S2.3): move these there.
    @ExceptionHandler(CredentialNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(CredentialNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage(), "CREDENTIAL_NOT_FOUND", null));
    }

    @ExceptionHandler(CredentialAccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(
            CredentialAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ex.getMessage(), "ACCESS_DENIED", null));
    }
}
