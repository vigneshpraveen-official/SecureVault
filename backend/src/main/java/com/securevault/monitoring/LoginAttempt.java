package com.securevault.monitoring;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Matches V8__login_attempts_and_alerts.sql. Immutable (no setters) — a compliance record, same
 * treatment as AuditLog. Keyed by email, not userId — an attempt against an email that doesn't
 * exist is still worth recording (M-45's "don't confirm the account exists" reasoning means
 * InvalidCredentialsException never reveals this, but the attempt itself is real).
 */
@Entity
@Table(name = "login_attempts")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(nullable = false)
    private boolean successful;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    @Column(name = "failure_reason", length = 100)
    private String failureReason;
}
