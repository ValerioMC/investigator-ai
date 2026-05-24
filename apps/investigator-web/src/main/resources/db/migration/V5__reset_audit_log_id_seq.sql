-- Previous rows were inserted with explicit IDs from Hibernate's audit_log_seq.
-- audit_log_id_seq (BIGSERIAL) never advanced. Reset it past the current max.
SELECT setval('audit_log_id_seq', GREATEST((SELECT COALESCE(MAX(id), 0) FROM audit_log) + 1, 1), false);
