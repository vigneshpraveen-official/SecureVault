package com.securevault.vault;

import com.securevault.common.response.ApiResponse;
import com.securevault.common.response.PagedResponse;
import com.securevault.security.UserPrincipal;
import com.securevault.vault.dto.CredentialCreateRequest;
import com.securevault.vault.dto.CredentialDetailResponse;
import com.securevault.vault.dto.CredentialResponse;
import com.securevault.vault.dto.CredentialSummaryResponse;
import com.securevault.vault.dto.CredentialUpdateRequest;
import com.securevault.vault.dto.PasswordHistoryVersionResponse;
import com.securevault.vault.dto.VaultHealthResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
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
@Validated
@Tag(name = "Vault", description = "Credential CRUD, search, trash, and password history")
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

    // sortBy is whitelisted against actual entity fields — an unvalidated sortBy is a 500 waiting
    // to happen (a bad JPA property path) and leaks the schema to the caller (P4.5/M-34).
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<CredentialSummaryResponse>>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") @Min(0) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer size,
            @RequestParam(defaultValue = "createdAt")
                    @Pattern(
                            regexp =
                                    "title|username|websiteUrl|category|favorite|strengthScore|createdAt|updatedAt",
                            message =
                                    "must be one of: title, username, websiteUrl, category,"
                                            + " favorite, strengthScore, createdAt, updatedAt")
                    String sortBy,
            @RequestParam(defaultValue = "desc")
                    @Pattern(regexp = "(?i)asc|desc", message = "must be asc or desc")
                    String direction,
            @RequestParam(required = false) Category category,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String website) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Vault retrieved successfully",
                        credentialService.listForUser(
                                principal.getId(),
                                page,
                                size,
                                sortBy,
                                direction,
                                category,
                                title,
                                username,
                                website)));
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

    // Same literal-beats-variable reasoning as /search and /health above.
    @GetMapping("/trash")
    public ResponseEntity<ApiResponse<List<CredentialSummaryResponse>>> trash(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(
                ApiResponse.success("Trash retrieved", credentialService.trash(principal.getId())));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<ApiResponse<List<PasswordHistoryVersionResponse>>> history(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Password history retrieved",
                        credentialService.getPasswordHistory(id, principal.getId())));
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

    // Soft delete as of P4.3 — still 204/no body, same envelope exemption as ever (RFC 9110
    // §15.3.5); the credential moves to the trash rather than being removed.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        credentialService.delete(id, principal.getId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/restore")
    public ResponseEntity<ApiResponse<CredentialResponse>> restore(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Credential restored", credentialService.restore(id, principal.getId())));
    }

    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<Void> permanentDelete(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        credentialService.permanentDelete(id, principal.getId());
        return ResponseEntity.noContent().build();
    }

    // 202, not 200/201 — the work hasn't happened yet when this returns (P4.6/M-40). Resolves
    // the TODO(S4.6) left in CredentialServiceImpl since S3.3.
    @PostMapping("/recompute-strength")
    public ResponseEntity<ApiResponse<Void>> recomputeStrength(
            @AuthenticationPrincipal UserPrincipal principal) {
        credentialService.recomputeStrengthForUser(principal.getId());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("Password strength recomputation started", null));
    }
}
