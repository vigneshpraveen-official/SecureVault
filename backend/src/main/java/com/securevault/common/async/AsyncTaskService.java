package com.securevault.common.async;

/**
 * Every method here runs off the request thread, in a DIFFERENT transaction and a DIFFERENT
 * security context (P4.6/M-40/M-41, ADR-020). Nothing that must roll back with a business operation
 * belongs here — that's exactly what AuditService stays synchronous for (P4.1). Any user identity a
 * method needs is passed explicitly as a parameter; SecurityContextHolder is empty on the async
 * thread. Feature-specific async work (e.g. CredentialService's bulk strength recompute) lives on
 * its own feature service instead of being generalized in here — this interface only holds
 * capabilities with no feature-specific data needs.
 */
public interface AsyncTaskService {

    /** Simulated only — real SMTP wiring is S5.6. Proves the async boundary, not delivery. */
    void sendNotificationEmail(String toEmail, String subject, String body);

    /** Best-effort, informational — contrast with AuditService.record(...), which is not. */
    void logActivity(String message);
}
