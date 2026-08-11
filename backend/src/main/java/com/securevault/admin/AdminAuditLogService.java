package com.securevault.admin;

import com.securevault.admin.dto.AdminAuditLogResponse;
import com.securevault.common.audit.AuditAction;
import com.securevault.common.response.PagedResponse;
import java.time.Instant;

public interface AdminAuditLogService {

    PagedResponse<AdminAuditLogResponse> list(
            int page, int size, Long userId, AuditAction action, Instant from, Instant to);
}
