-- S3.3/M-29: adds a dedicated "when did the password itself last change" timestamp.
-- Deliberately NOT reusing credentials.updated_at, which also changes on an unrelated edit
-- (renaming a credential, changing its category) and would silently reset password-age
-- tracking on every edit — see docs/password-policy.md §3 and docs/decisions.md.
--
-- strength_score already exists (V1__init.sql) and was simply unmapped until this session.
--
-- Backfill: existing rows get now() — their true original change date isn't known, so "age
-- starts counting from this migration" is the honest, disclosed baseline (docs/db-design.md).

ALTER TABLE credentials
    ADD COLUMN password_changed_at TIMESTAMPTZ NOT NULL DEFAULT now();
