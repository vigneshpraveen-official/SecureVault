package com.securevault.vault;

import com.securevault.common.response.ApiResponse;
import com.securevault.security.UserPrincipal;
import com.securevault.vault.dto.CredentialCreateRequest;
import com.securevault.vault.dto.CredentialDetailResponse;
import com.securevault.vault.dto.CredentialResponse;
import com.securevault.vault.dto.CredentialSummaryResponse;
import com.securevault.vault.dto.CredentialUpdateRequest;
import com.securevault.vault.dto.VaultHealthResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    public ResponseEntity<ApiResponse<CredentialDetailResponse>> getById(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Credential retrieved successfully",
                        credentialService.getByIdForUser(id, principal.getId())));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CredentialSummaryResponse>>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) Category category) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Vault retrieved successfully",
                        credentialService.listForUser(principal.getId(), category)));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<CredentialSummaryResponse>>> search(
            @AuthenticationPrincipal UserPrincipal principal, @RequestParam String q) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Search results", credentialService.search(principal.getId(), q)));
    }

    // Literal "/health" ranks above the "/{id}" pattern in Spring's path matching (same as
    // "/search" already does), so this never collides with getById.
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<VaultHealthResponse>> health(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Vault health computed", credentialService.getHealth(principal.getId())));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CredentialResponse>> update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody CredentialUpdateRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Credential updated successfully",
                        credentialService.update(id, principal.getId(), request)));
    }

    // The one endpoint shape exempt from the ApiResponse envelope: HTTP forbids a response body
    // on 204 (RFC 9110 §15.3.5), so there is nothing an envelope could wrap (ADR-established
    // S1.4, reaffirmed here for P2.3/M-27).
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        credentialService.delete(id, principal.getId());
        return ResponseEntity.noContent().build();
    }
}
