package com.securevault.sharing;

import com.securevault.common.response.ApiResponse;
import com.securevault.security.UserPrincipal;
import com.securevault.sharing.dto.ShareCreateRequest;
import com.securevault.sharing.dto.SharePermissionUpdateRequest;
import com.securevault.sharing.dto.ShareResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/share")
@RequiredArgsConstructor
@Tag(name = "Sharing", description = "Credential sharing, permissions, and revocation")
public class CredentialShareController {

    private final CredentialShareService credentialShareService;

    @PostMapping
    public ResponseEntity<ApiResponse<ShareResponse>> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ShareCreateRequest request) {
        ShareResponse response = credentialShareService.create(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Credential shared successfully", response));
    }

    @GetMapping("/received")
    public ResponseEntity<ApiResponse<List<ShareResponse>>> received(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Shares received", credentialShareService.received(principal.getId())));
    }

    @GetMapping("/sent")
    public ResponseEntity<ApiResponse<List<ShareResponse>>> sent(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(
                ApiResponse.success("Shares sent", credentialShareService.sent(principal.getId())));
    }

    @PutMapping("/{shareId}")
    public ResponseEntity<ApiResponse<ShareResponse>> updatePermission(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long shareId,
            @Valid @RequestBody SharePermissionUpdateRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Share permission updated",
                        credentialShareService.updatePermission(
                                shareId, principal.getId(), request)));
    }

    @DeleteMapping("/{shareId}")
    public ResponseEntity<Void> revoke(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long shareId) {
        credentialShareService.revoke(shareId, principal.getId());
        return ResponseEntity.noContent().build();
    }
}
