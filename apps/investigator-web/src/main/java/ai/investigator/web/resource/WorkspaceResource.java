package ai.investigator.web.resource;

import ai.investigator.web.entity.Workspace;
import ai.investigator.web.repository.WorkspaceRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/web/v1/workspaces")
public class WorkspaceResource {

    private final WorkspaceRepository repo;

    public WorkspaceResource(WorkspaceRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public ResponseEntity<List<Workspace>> list() {
        return ResponseEntity.ok(repo.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Workspace> get(@PathVariable UUID id) {
        return repo.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> create(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        if (name == null || name.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "name required"));

        var ws = new Workspace();
        ws.name = name;
        ws.slug = name.toLowerCase().replaceAll("[^a-z0-9]", "-").replaceAll("-+", "-");
        repo.save(ws);
        return ResponseEntity.status(201).body(ws);
    }
}
