package ai.investigator.web.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "investigation_sessions")
public class InvestigationSession {

    public enum Status { PENDING, RUNNING, COMPLETED, FAILED }

    @Id
    @GeneratedValue
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    public Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    public User user;

    @Column(nullable = false, columnDefinition = "TEXT")
    public String query;

    @Column(nullable = false)
    public short depth = 3;

    @Column(name = "focus_entities", columnDefinition = "TEXT[]")
    public String[] focusEntities;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public Status status = Status.PENDING;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    public String report;

    @Column(name = "finding_count")
    public short findingCount = 0;

    @Column(name = "high_count")
    public short highCount = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    public String errorMessage;

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "started_at")
    public OffsetDateTime startedAt;

    @Column(name = "completed_at")
    public OffsetDateTime completedAt;
}
