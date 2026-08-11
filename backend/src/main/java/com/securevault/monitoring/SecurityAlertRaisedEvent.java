package com.securevault.monitoring;

/**
 * Published whenever a SecurityAlert is persisted (SecurityAlertServiceImpl). No listener exists
 * yet in P5.5 — S5.6 adds an @TransactionalEventListener(phase = AFTER_COMMIT) that turns this into
 * a Notification row + async email, per that session's explicit application-event-model
 * requirement. Carries ids only, not the entity, so a listener never accidentally works with a
 * detached/stale JPA object outside the original transaction.
 */
public record SecurityAlertRaisedEvent(
        Long alertId, Long userId, AlertType type, AlertSeverity severity, String message) {}
