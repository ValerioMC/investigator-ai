package ai.investigator.web.service;

import ai.investigator.web.entity.AuditLog;
import ai.investigator.web.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuditLogService {

    private final AuditLogRepository repo;

    public AuditLogService(AuditLogRepository repo) {
        this.repo = repo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog log(UUID workspaceId, UUID userId, String action,
                        String entityType, String entityId, String details) {
        var entry = new AuditLog();
        entry.workspaceId = workspaceId;
        entry.userId = userId;
        entry.action = action;
        entry.entityType = entityType;
        entry.entityId = entityId;
        entry.details = details;
        return repo.save(entry);
    }
}
