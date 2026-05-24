package ai.investigator.web.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "workspace_id")
    public UUID workspaceId;

    @Column(name = "user_id")
    public UUID userId;

    @Column(nullable = false, length = 100)
    public String action;

    @Column(name = "entity_type", length = 50)
    public String entityType;

    @Column(name = "entity_id")
    public String entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    public String details;

    @Column(name = "ip_address", length = 45)
    public String ipAddress;

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt = OffsetDateTime.now();
}
