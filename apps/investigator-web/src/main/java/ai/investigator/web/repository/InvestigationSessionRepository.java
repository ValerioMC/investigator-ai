package ai.investigator.web.repository;

import ai.investigator.web.entity.InvestigationSession;
import ai.investigator.web.entity.InvestigationSession.Status;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface InvestigationSessionRepository extends JpaRepository<InvestigationSession, UUID> {

    List<InvestigationSession> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId, Pageable pageable);

    long countByWorkspaceId(UUID workspaceId);

    long countByWorkspaceIdAndStatus(UUID workspaceId, Status status);

    @Query("SELECT COUNT(s) FROM InvestigationSession s WHERE s.workspace.id = :workspaceId AND s.createdAt >= :since")
    long countByWorkspaceIdAndCreatedAtAfter(@Param("workspaceId") UUID workspaceId,
                                              @Param("since") OffsetDateTime since);
}
