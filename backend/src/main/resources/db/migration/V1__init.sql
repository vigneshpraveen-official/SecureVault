-- Phase 1 baseline schema: users and credentials only.
-- Full target schema documented in docs/db-design.md; every other table (password_history,
-- credential_shares, audit_logs, login_attempts, devices, refresh_tokens, notifications)
-- gets its own migration in the session that needs it. Never edit this file once applied —
-- future changes are always a new V<n>__*.sql.

CREATE TABLE users (
    id                      BIGSERIAL PRIMARY KEY,
    full_name               VARCHAR(100) NOT NULL,
    email                   VARCHAR(150) NOT NULL,
    password_hash           VARCHAR(60) NOT NULL,
    role                    VARCHAR(20) NOT NULL,
    mfa_enabled             BOOLEAN NOT NULL DEFAULT false,
    mfa_secret              VARCHAR(255),
    account_locked          BOOLEAN NOT NULL DEFAULT false,
    failed_login_attempts   INT NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE credentials (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    title               VARCHAR(150) NOT NULL,
    username            VARCHAR(150),
    encrypted_password  TEXT NOT NULL,
    website_url         VARCHAR(255),
    notes               TEXT,
    category            VARCHAR(30) NOT NULL,
    favorite            BOOLEAN NOT NULL DEFAULT false,
    strength_score      SMALLINT,
    deleted             BOOLEAN NOT NULL DEFAULT false,
    deleted_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_credentials_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_credentials_title ON credentials (title);
CREATE INDEX idx_credentials_category ON credentials (category);
CREATE INDEX idx_credentials_user_id_deleted ON credentials (user_id, deleted);
