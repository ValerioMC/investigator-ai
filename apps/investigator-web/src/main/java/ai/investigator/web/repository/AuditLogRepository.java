package ai.investigator.web.repository;

import ai.investigator.web.entity.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId, Pageable pageable);
}
