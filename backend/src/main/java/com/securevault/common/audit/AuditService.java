package com.securevault.common.audit;

public interface AuditService {

    /**
     * Must be called from inside the SAME @Transactional business method it is auditing (P4.1) — an
     * AOP aspect would be cleaner, but a failure here has to roll back the business write with it,
     * which only works if both go through the same transactional boundary. details must never
     * contain a password, token, or decrypted value.
     */
    void record(
            AuditAction action, String entityType, Long entityId, Long performedBy, String details);
}
