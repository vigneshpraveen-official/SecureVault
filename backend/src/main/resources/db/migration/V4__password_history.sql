-- S4.2/M-35,M-36: one Credential -> many PasswordHistory. Rows are immutable once written
-- (never UPDATEd), and version is a per-credential monotonically increasing counter starting
-- at 1, enforced by the unique constraint below rather than trusted to application code alone.
--
-- Deliberately NO "ON DELETE CASCADE": S4.3's permanent-delete endpoint must delete history
-- explicitly, in application code, before deleting the credential (P4.3's exact requirement).
-- A plain FK (default RESTRICT) means that ordering is actually enforced by the database, not
-- just documented — deleting the credential first would fail loudly instead of silently
-- cascading regardless of which order the code got wrong.

CREATE TABLE password_history (
    id                  BIGSERIAL PRIMARY KEY,
    credential_id       BIGINT NOT NULL REFERENCES credentials (id),
    encrypted_password  TEXT NOT NULL,
    version             INT NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_password_history_credential_version UNIQUE (credential_id, version)
);

CREATE INDEX idx_password_history_credential_id ON password_history (credential_id);
