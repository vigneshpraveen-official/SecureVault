-- P5.1/M-42: credential sharing with READ|EDIT permission. Deliberately no ON DELETE CASCADE on
-- credential_id (same reasoning as V4's password_history) — CredentialServiceImpl.permanentDelete
-- must explicitly delete a credential's shares first, keeping "what disappears on permanent
-- delete" an explicit business decision in code, not an implicit DB cascade.
CREATE TABLE credential_shares (
    id                   BIGSERIAL PRIMARY KEY,
    credential_id        BIGINT NOT NULL REFERENCES credentials(id),
    owner_id             BIGINT NOT NULL REFERENCES users(id),
    shared_with_user_id  BIGINT NOT NULL REFERENCES users(id),
    permission           VARCHAR(10) NOT NULL,
    shared_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at           TIMESTAMPTZ,
    active               BOOLEAN NOT NULL DEFAULT true
);

-- Partial unique index, not a table-wide UNIQUE constraint: a revoked share (active=false) must
-- not block re-sharing the same credential to the same user later (M-42's "duplicate active
-- shares" wording is deliberate — duplicate INACTIVE rows are fine and expected over time).
CREATE UNIQUE INDEX uq_credential_shares_active
    ON credential_shares (credential_id, shared_with_user_id)
    WHERE active = true;

CREATE INDEX idx_credential_shares_shared_with ON credential_shares (shared_with_user_id);
CREATE INDEX idx_credential_shares_credential ON credential_shares (credential_id);
CREATE INDEX idx_credential_shares_owner ON credential_shares (owner_id);
