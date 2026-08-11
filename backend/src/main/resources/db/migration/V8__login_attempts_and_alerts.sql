-- P5.5: every login attempt, success or failure, plus persisted security alerts.
CREATE TABLE login_attempts (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(150) NOT NULL,
    successful      BOOLEAN NOT NULL,
    ip_address      VARCHAR(45),
    user_agent      VARCHAR(255),
    attempted_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    failure_reason  VARCHAR(100)
);
CREATE INDEX idx_login_attempts_email ON login_attempts (email);
CREATE INDEX idx_login_attempts_attempted_at ON login_attempts (attempted_at);

CREATE TABLE security_alerts (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id),
    type        VARCHAR(50) NOT NULL,
    severity    VARCHAR(20) NOT NULL,
    message     VARCHAR(500) NOT NULL,
    resolved    BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_security_alerts_user ON security_alerts (user_id);
