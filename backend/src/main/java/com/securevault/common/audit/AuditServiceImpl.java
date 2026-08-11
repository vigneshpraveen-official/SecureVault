package com.securevault.common.audit;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Test-only knob to prove the rollback guarantee (P4.1) — throws before the audit row is
     * persisted, so a real test/demo can show the credential write it was called from also rolls
     * back. Defaults false and MUST stay false/unset in every real profile; never flipped by
     * application code, only by an operator setting the env var for one deliberate run.
     */
    @Value("${app.testing.force-audit-failure:false}")
    private boolean forceAuditFailure;

    @Override
    public void record(
            AuditAction action,
            String entityType,
            Long entityId,
            Long performedBy,
            String details) {
        if (forceAuditFailure) {
            throw new IllegalStateException(
                    "Simulated audit failure (app.testing.force-audit-failure=true) — proves the"
                            + " credential write in the same transaction rolls back with it"
                            + " (P4.1/M-31).");
        }

        HttpServletRequest request = currentRequest();
        AuditLog log =
                AuditLog.builder()
                        .action(action)
                        .entityType(entityType)
                        .entityId(entityId)
                        .performedBy(performedBy)
                        .timestamp(Instant.now())
                        .ipAddress(request == null ? null : request.getRemoteAddr())
                        .userAgent(request == null ? null : request.getHeader("User-Agent"))
                        .details(details)
                        .build();
        auditLogRepository.save(log);
    }

    // Looked up per-call via RequestContextHolder rather than constructor-injected — a
    // constructor-injected HttpServletRequest is a request-scoped proxy that throws
    // IllegalStateException ("no thread-bound request") the moment this is called from anywhere
    // that isn't a real HTTP request thread: DevDataSeeder at startup (S4.5), and any @Async
    // background work (S4.6). Business events from those callers still get audited, just without
    // request metadata that genuinely doesn't exist for them — null, not a crash.
    private HttpServletRequest currentRequest() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest();
        }
        return null;
    }
}
