package com.securevault.admin;

import com.securevault.admin.dto.AdminAuditLogResponse;
import com.securevault.admin.dto.AdminStatsResponse;
import com.securevault.admin.dto.AdminUserResponse;
import com.securevault.admin.dto.AdminUserStatusUpdateRequest;
import com.securevault.common.audit.AuditAction;
import com.securevault.common.response.ApiResponse;
import com.securevault.common.response.PagedResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * P5.7/P5.8: every route here is @PreAuthorize("hasRole('ADMIN')") (method security enabled in
 * SecurityConfig as of this session) — a non-admin JWT gets 403 on all four, verified live.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Admin-only: platform stats, user management, audit log viewer")
public class AdminController {

    private final AdminStatsService adminStatsService;
    private final AdminUserService adminUserService;
    private final AdminAuditLogService adminAuditLogService;

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdminStatsResponse>> stats() {
        return ResponseEntity.ok(
                ApiResponse.success("Admin stats computed", adminStatsService.computeStats()));
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PagedResponse<AdminUserResponse>>> users(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(
                ApiResponse.success("Users retrieved", adminUserService.list(page, size, search)));
    }

    @PutMapping("/users/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdminUserResponse>> updateUserStatus(
            @PathVariable Long id, @Valid @RequestBody AdminUserStatusUpdateRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "User status updated", adminUserService.updateStatus(id, request)));
    }

    @GetMapping("/audit-logs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PagedResponse<AdminAuditLogResponse>>> auditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Audit logs retrieved",
                        adminAuditLogService.list(page, size, userId, action, from, to)));
    }
}
