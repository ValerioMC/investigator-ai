-- Ensure audit_log_seq starts after any rows that may have been inserted
-- via audit_log_id_seq (BIGSERIAL default) before Hibernate took over.
SELECT setval('audit_log_seq', GREATEST((SELECT COALESCE(MAX(id), 0) FROM audit_log) + 1, 1), false);
