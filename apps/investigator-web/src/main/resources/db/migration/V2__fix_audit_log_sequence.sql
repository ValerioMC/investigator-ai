-- Hibernate 6 / PanacheEntity defaults to {table}_seq naming.
-- PostgreSQL BIGSERIAL creates {table}_id_seq. Bridge the gap.
CREATE SEQUENCE IF NOT EXISTS audit_log_seq
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
