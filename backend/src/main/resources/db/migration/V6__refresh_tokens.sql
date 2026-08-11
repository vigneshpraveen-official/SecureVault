-- P5.2/M-51-ish: durable refresh-token record backing rotation + reuse detection. Only a SHA-256
-- hash of the raw token is ever stored (never the token itself) — same principle as password_hash.
-- token_family groups every token descended from one login: rotation keeps the family_id,
-- reuse of an already-rotated (revoked) token revokes the whole family in one statement.
CREATE TABLE refresh_tokens (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users(id),
    token_hash    VARCHAR(64) NOT NULL UNIQUE,
    token_family  VARCHAR(36) NOT NULL,
    expires_at    TIMESTAMPTZ NOT NULL,
    revoked       BOOLEAN NOT NULL DEFAULT false,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (token_family);
