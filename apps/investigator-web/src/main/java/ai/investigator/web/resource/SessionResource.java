package ai.investigator.web.resource;

import ai.investigator.agents.supervisor.InvestigationOrchestrator;
import ai.investigator.web.dto.SessionDto;
import ai.investigator.web.entity.InvestigationSession;
import ai.investigator.web.entity.InvestigationSession.Status;
import ai.investigator.web.entity.Workspace;
import ai.investigator.web.repository.InvestigationSessionRepository;
import ai.investigator.web.repository.WorkspaceRepository;
import ai.investigator.web.service.AuditLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/web/v1/sessions")
public class SessionResource {

    private static final Logger log = LoggerFactory.getLogger(SessionResource.class);

    private static final UUID DEFAULT_WORKSPACE =
        UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final InvestigationOrchestrator orchestrator;
    private final ObjectMapper mapper = new ObjectMapper();
    private final InvestigationSessionRepository sessions;
    private final WorkspaceRepository workspaces;
    private final AuditLogService auditLog;

    public SessionResource(InvestigationOrchestrator orchestrator,
                            InvestigationSessionRepository sessions,
                            WorkspaceRepository workspaces,
                            AuditLogService auditLog) {
        this.orchestrator = orchestrator;
        this.sessions = sessions;
        this.workspaces = workspaces;
        this.auditLog = auditLog;
    }

    @GetMapping
    public ResponseEntity<List<SessionDto.Summary>> list(
            @RequestParam(defaultValue = "20") int limit) {
        var list = sessions.findByWorkspaceIdOrderByCreatedAtDesc(DEFAULT_WORKSPACE,
            PageRequest.of(0, limit));
        return ResponseEntity.ok(list.stream().map(SessionDto.Summary::from).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SessionDto.Detail> get(@PathVariable UUID id) {
        return sessions.findById(id)
            .map(s -> ResponseEntity.ok(SessionDto.Detail.from(s)))
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<SessionDto.Detail> create(@RequestBody SessionDto.CreateRequest req) {
        // create session record
        InvestigationSession session = createSession(req);
        auditLog.log(DEFAULT_WORKSPACE, null, "INVESTIGATION_STARTED",
            "SESSION", session.id.toString(), null);

        // LLM call outside transaction
        String focusHint = req.focusEntities().isEmpty() ? "" :
            " Focus on: " + String.join(", ", req.focusEntities()) + ".";
        String prompt = req.query() + focusHint + " Depth: " + req.depth();

        try {
            String rawText = orchestrator.investigate(prompt);
            String rawReport = extractJson(rawText);
            if (rawReport == null) {
                log.warn("LLM did not return valid JSON for session {}", session.id);
                rawReport = "{\"query\":\"\",\"summary\":\"LLM response was not valid JSON.\","
                    + "\"findings\":[],\"entityMap\":{\"persons\":[],\"companies\":[],\"contracts\":[]},"
                    + "\"recommendedFollowUps\":[],\"disclaimer\":\"Response parsing failed.\"}";
            }
            final String finalReport = rawReport;
            session = completeSession(session.id, finalReport);
            auditLog.log(DEFAULT_WORKSPACE, null, "INVESTIGATION_COMPLETED",
                "SESSION", session.id.toString(), null);
        } catch (Exception e) {
            String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            session = failSession(session.id, errMsg);
            auditLog.log(DEFAULT_WORKSPACE, null, "INVESTIGATION_FAILED",
                "SESSION", session.id.toString(),
                "{\"error\":\"" + errMsg.replace("\"", "'") + "\"}");
        }

        return ResponseEntity.status(201).body(SessionDto.Detail.from(session));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        if (!sessions.existsById(id)) return ResponseEntity.notFound().build();
        auditLog.log(DEFAULT_WORKSPACE, null, "INVESTIGATION_DELETED", "SESSION", id.toString(), null);
        sessions.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stats")
    public ResponseEntity<SessionDto.StatsResponse> stats() {
        long total = sessions.countByWorkspaceId(DEFAULT_WORKSPACE);
        long lastHour = sessions.countByWorkspaceIdAndCreatedAtAfter(DEFAULT_WORKSPACE,
            OffsetDateTime.now().minusHours(1));
        long completed = sessions.countByWorkspaceIdAndStatus(DEFAULT_WORKSPACE, Status.COMPLETED);
        long failed = sessions.countByWorkspaceIdAndStatus(DEFAULT_WORKSPACE, Status.FAILED);
        return ResponseEntity.ok(new SessionDto.StatsResponse(total, lastHour, completed, failed));
    }

    @Transactional
    protected InvestigationSession createSession(SessionDto.CreateRequest req) {
        Workspace ws = workspaces.findById(DEFAULT_WORKSPACE).orElseThrow();
        var session = new InvestigationSession();
        session.workspace = ws;
        session.query = req.query();
        session.depth = (short) req.depth();
        session.focusEntities = req.focusEntities().toArray(String[]::new);
        session.status = Status.RUNNING;
        session.startedAt = OffsetDateTime.now();
        return sessions.save(session);
    }

    @Transactional
    protected InvestigationSession completeSession(UUID id, String report) {
        InvestigationSession s = sessions.findById(id).orElseThrow();
        s.report = report;
        s.status = Status.COMPLETED;
        s.completedAt = OffsetDateTime.now();
        try {
            var root = mapper.readTree(report);
            var findings = root.path("findings");
            s.findingCount = (short) findings.size();
            s.highCount = (short) countByConfidence(findings, "HIGH");
        } catch (Exception ignored) {}
        return sessions.save(s);
    }

    @Transactional
    protected InvestigationSession failSession(UUID id, String errMsg) {
        InvestigationSession s = sessions.findById(id).orElseThrow();
        s.status = Status.FAILED;
        s.errorMessage = errMsg;
        s.completedAt = OffsetDateTime.now();
        return sessions.save(s);
    }

    private int countByConfidence(com.fasterxml.jackson.databind.JsonNode findings, String level) {
        int count = 0;
        for (var f : findings) {
            if (level.equals(f.path("confidence").asText())) count++;
        }
        return count;
    }

    private String extractJson(String raw) {
        if (raw == null || raw.isBlank()) return null;
        log.warn("Raw LLM response (first 1000 chars): {}",
            raw.length() > 1000 ? raw.substring(0, 1000) + "..." : raw);

        String cleaned = raw.replaceAll("(?s)<think>.*?</think>", "").trim();
        cleaned = cleaned.replaceAll("(?s)```(?:json)?\\s*", "").trim();

        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end <= start) {
            log.warn("Could not find JSON object in LLM response. Cleaned: {}",
                cleaned.length() > 300 ? cleaned.substring(0, 300) : cleaned);
            return null;
        }
        String candidate = cleaned.substring(start, end + 1);

        try {
            mapper.readTree(candidate);
            return candidate;
        } catch (Exception e) {
            log.warn("Extracted text is not valid JSON: {}... Error: {}",
                candidate.length() > 200 ? candidate.substring(0, 200) : candidate, e.getMessage());
            return null;
        }
    }
}
