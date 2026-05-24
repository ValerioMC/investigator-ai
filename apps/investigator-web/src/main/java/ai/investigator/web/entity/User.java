package ai.investigator.web.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue
    public UUID id;

    @Column(nullable = false, unique = true)
    public String email;

    @Column(nullable = false, unique = true)
    public String username;

    @Column(name = "display_name")
    public String displayName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    public Workspace workspace;

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "last_seen_at")
    public OffsetDateTime lastSeenAt;
}
