package com.securevault.admin;

import com.securevault.admin.dto.AdminAuditLogResponse;
import com.securevault.common.audit.AuditAction;
import com.securevault.common.audit.AuditLog;
import com.securevault.common.audit.AuditLogRepository;
import com.securevault.common.audit.AuditLogSpecifications;
import com.securevault.common.response.PagedResponse;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminAuditLogServiceImpl implements AdminAuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Override
    public PagedResponse<AdminAuditLogResponse> list(
            int page, int size, Long userId, AuditAction action, Instant from, Instant to) {
        Specification<AuditLog> spec = (root, query, cb) -> cb.conjunction();
        if (userId != null) {
            spec = spec.and(AuditLogSpecifications.performedBy(userId));
        }
        if (action != null) {
            spec = spec.and(AuditLogSpecifications.action(action));
        }
        if (from != null) {
            spec = spec.and(AuditLogSpecifications.timestampAfter(from));
        }
        if (to != null) {
            spec = spec.and(AuditLogSpecifications.timestampBefore(to));
        }

        Page<AuditLog> result =
                auditLogRepository.findAll(
                        spec,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp")));
        return new PagedResponse<>(
                result.getContent().stream().map(AdminAuditLogServiceImpl::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast(),
                result.hasNext());
    }

    private static AdminAuditLogResponse toResponse(AuditLog log) {
        return new AdminAuditLogResponse(
                log.getId(),
                log.getAction().name(),
                log.getEntityType(),
                log.getEntityId(),
                log.getPerformedBy(),
                log.getTimestamp(),
                log.getIpAddress(),
                log.getUserAgent(),
                log.getDetails());
    }
}
