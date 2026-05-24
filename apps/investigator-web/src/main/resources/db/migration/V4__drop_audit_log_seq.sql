-- AuditLog now uses IDENTITY (BIGSERIAL / audit_log_id_seq) for ID generation.
-- The Hibernate-managed audit_log_seq is no longer needed.
DROP SEQUENCE IF EXISTS audit_log_seq;
