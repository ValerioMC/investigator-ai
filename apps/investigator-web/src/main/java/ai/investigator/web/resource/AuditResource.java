package ai.investigator.web.resource;

import ai.investigator.web.entity.AuditLog;
import ai.investigator.web.repository.AuditLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/web/v1/audit")
public class AuditResource {

    private static final UUID DEFAULT_WORKSPACE =
        UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final AuditLogRepository repo;

    public AuditResource(AuditLogRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public ResponseEntity<List<AuditLog>> list(@RequestParam(defaultValue = "50") int limit) {
        var entries = repo.findByWorkspaceIdOrderByCreatedAtDesc(DEFAULT_WORKSPACE,
            PageRequest.of(0, Math.min(limit, 200)));
        return ResponseEntity.ok(entries);
    }
}
