-- S4.1/M-31,M-32: append-only audit trail. No FK to users or credentials on purpose — an audit
-- row must survive permanent deletion of the entity it describes (see docs/decisions.md), and
-- performed_by is a plain id, not a JPA relationship, so listing audit rows never risks N+1
-- against the users table (docs/evidence/milestone-2/n-plus-one.md).

CREATE TABLE audit_logs (
    id            BIGSERIAL PRIMARY KEY,
    action        VARCHAR(30) NOT NULL,
    entity_type   VARCHAR(50) NOT NULL,
    entity_id     BIGINT NOT NULL,
    performed_by  BIGINT NOT NULL,
    timestamp     TIMESTAMPTZ NOT NULL DEFAULT now(),
    ip_address    VARCHAR(45),
    user_agent    VARCHAR(255),
    details       TEXT
);

CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX idx_audit_logs_performed_by ON audit_logs (performed_by);
