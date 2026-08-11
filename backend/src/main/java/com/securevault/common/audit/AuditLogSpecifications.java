package com.securevault.common.audit;

import java.time.Instant;
import org.springframework.data.jpa.domain.Specification;

/** Same composable-Specification pattern as vault.CredentialSpecifications (D-12/ADR-021). */
public final class AuditLogSpecifications {

    private AuditLogSpecifications() {}

    public static Specification<AuditLog> performedBy(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("performedBy"), userId);
    }

    public static Specification<AuditLog> action(AuditAction action) {
        return (root, query, cb) -> cb.equal(root.get("action"), action);
    }

    public static Specification<AuditLog> timestampAfter(Instant from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("timestamp"), from);
    }

    public static Specification<AuditLog> timestampBefore(Instant to) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("timestamp"), to);
    }
}
