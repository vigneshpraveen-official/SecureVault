-- S0.1 bootstrap baseline. No entities exist yet, so this migration creates nothing.
-- It exists only so Flyway has a history to build on and ddl-auto=validate has zero
-- entities to validate against. The real schema (users, credentials, ...) lands in
-- S0.3 as V1__init.sql per docs/securevault_master.md §16.
SELECT 1;
