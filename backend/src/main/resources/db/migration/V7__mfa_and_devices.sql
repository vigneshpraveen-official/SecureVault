-- P5.4/M-... : MFA backup codes, device/session tracking, and refresh-token-to-device linkage
-- (so DELETE /api/monitoring/devices/{id} can actually revoke that device's sessions).

CREATE TABLE mfa_backup_codes (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id),
    code_hash   VARCHAR(60) NOT NULL,
    used        BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_mfa_backup_codes_user ON mfa_backup_codes (user_id);

CREATE TABLE devices (
    id                   BIGSERIAL PRIMARY KEY,
    user_id              BIGINT NOT NULL REFERENCES users(id),
    device_fingerprint   VARCHAR(64) NOT NULL,
    device_name          VARCHAR(150),
    ip_address           VARCHAR(45),
    user_agent           VARCHAR(255),
    last_seen_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    trusted              BOOLEAN NOT NULL DEFAULT true,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_devices_user_fingerprint UNIQUE (user_id, device_fingerprint)
);

ALTER TABLE refresh_tokens ADD COLUMN device_fingerprint VARCHAR(64);
