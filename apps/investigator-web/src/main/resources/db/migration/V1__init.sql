-- Workspaces: multi-tenant grouping for investigations
CREATE TABLE workspaces (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    slug        VARCHAR(100) NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Users
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL UNIQUE,
    username        VARCHAR(100) NOT NULL UNIQUE,
    display_name    VARCHAR(255),
    workspace_id    UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at    TIMESTAMPTZ
);

-- Investigation sessions: each run of the SupervisorAgent
CREATE TABLE investigation_sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id         UUID REFERENCES users(id) ON DELETE SET NULL,
    query           TEXT NOT NULL,
    depth           SMALLINT NOT NULL DEFAULT 3,
    focus_entities  TEXT[],                 -- array of entity names/ids
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING','RUNNING','COMPLETED','FAILED')),
    report          JSONB,                  -- full InvestigationReport JSON
    finding_count   SMALLINT DEFAULT 0,
    high_count      SMALLINT DEFAULT 0,
    error_message   TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ
);

CREATE INDEX idx_sessions_workspace ON investigation_sessions(workspace_id);
CREATE INDEX idx_sessions_status    ON investigation_sessions(status);
CREATE INDEX idx_sessions_created   ON investigation_sessions(created_at DESC);

-- Audit log: every user action
CREATE TABLE audit_log (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    UUID REFERENCES workspaces(id) ON DELETE SET NULL,
    user_id         UUID REFERENCES users(id) ON DELETE SET NULL,
    action          VARCHAR(100) NOT NULL,  -- e.g. INVESTIGATION_STARTED, ENTITY_VIEWED
    entity_type     VARCHAR(50),            -- SESSION, PERSON, COMPANY, etc.
    entity_id       TEXT,
    details         JSONB,
    ip_address      VARCHAR(45),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_workspace ON audit_log(workspace_id);
CREATE INDEX idx_audit_created   ON audit_log(created_at DESC);

-- Seed: default workspace
INSERT INTO workspaces (id, name, slug) VALUES
    ('00000000-0000-0000-0000-000000000001', 'Default Workspace', 'default');
