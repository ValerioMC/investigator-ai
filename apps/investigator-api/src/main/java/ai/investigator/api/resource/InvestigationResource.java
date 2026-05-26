package ai.investigator.api.resource;

import ai.investigator.agents.observability.LangfuseTraceContext;
import ai.investigator.agents.supervisor.SupervisorAgent;
import ai.investigator.domain.report.EntityMap;
import ai.investigator.domain.report.InvestigationReport;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/v1")
public class InvestigationResource {

    private static final Logger log = LoggerFactory.getLogger(InvestigationResource.class);

    private final SupervisorAgent supervisor;
    private final MeterRegistry metrics;
    // SNAKE_CASE to match the LLM's output field names (entity_map, recommended_follow_ups, agent_source)
    private final ObjectMapper mapper = new ObjectMapper()
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);

    private final AtomicLong investigationCount = new AtomicLong(0);

    public InvestigationResource(SupervisorAgent supervisor, MeterRegistry metrics) {
        this.supervisor = supervisor;
        this.metrics = metrics;
    }

    @PostMapping("/investigate")
    public ResponseEntity<InvestigationReport> investigate(@RequestBody InvestigateRequest request) {
        var timer = Timer.start(metrics);
        investigationCount.incrementAndGet();
        metrics.counter("investigations.total").increment();

        // Group every LLM call of this investigation under one Langfuse session.
        String session = UUID.randomUUID().toString();
        LangfuseTraceContext.setSession(session);
        long t0 = System.currentTimeMillis();
        log.info("[INVESTIGATE-START] session={} query='{}' focus={}",
            session, request.query(), request.focusEntities());
        try {
            String prompt = buildPrompt(request);
            log.debug("[INVESTIGATE] supervisor prompt: {}", prompt);

            String rawResponse = supervisor.investigate(prompt);
            log.info("[INVESTIGATE] supervisor returned {} chars in {} ms",
                rawResponse != null ? rawResponse.length() : 0,
                System.currentTimeMillis() - t0);
            log.debug("[INVESTIGATE] raw response: {}", rawResponse);

            InvestigationReport report = parseOrWrap(rawResponse, request.query());

            timer.stop(metrics.timer("investigations.latency"));
            metrics.counter("investigations.success").increment();
            log.info("[INVESTIGATE-END] session={} findings={} total={} ms",
                session, report.findings() != null ? report.findings().size() : 0,
                System.currentTimeMillis() - t0);
            return ResponseEntity.ok(report);

        } catch (Exception e) {
            metrics.counter("investigations.errors").increment();
            timer.stop(metrics.timer("investigations.latency"));
            log.error("[INVESTIGATE-FAIL] session={} after {} ms: {}",
                session, System.currentTimeMillis() - t0, e.toString(), e);
            throw e;
        } finally {
            LangfuseTraceContext.clear();
        }
    }

    @GetMapping("/metrics/investigation-stats")
    public ResponseEntity<InvestigationStats> investigationStats() {
        return ResponseEntity.ok(new InvestigationStats(investigationCount.get(), Instant.now().toString()));
    }

    private String buildPrompt(InvestigateRequest request) {
        var sb = new StringBuilder("USER QUERY: ").append(request.query());
        if (request.focusEntities() != null && !request.focusEntities().isEmpty()) {
            sb.append("\nFOCUS ENTITIES: ").append(String.join(", ", request.focusEntities()));
        }
        return sb.toString();
    }

    private InvestigationReport parseOrWrap(String raw, String originalQuery) {
        try {
            String json = raw.replaceAll("(?s)<think>.*?</think>", "").trim();
            json = json.replaceAll("(?s)```(?:json)?\\s*", "").trim();
            int start = json.indexOf('{');
            int end   = json.lastIndexOf('}');
            if (start != -1 && end > start) json = json.substring(start, end + 1);
            return mapper.readValue(json, InvestigationReport.class);
        } catch (Exception e) {
            return new InvestigationReport(
                originalQuery,
                raw.length() > 500 ? raw.substring(0, 500) + "..." : raw,
                List.of(),
                EntityMap.empty(),
                List.of("Review raw agent output manually"),
                "This report is a journalistic aid. Claims require editorial verification before publication."
            );
        }
    }

    public record InvestigationStats(long totalInvestigations, String asOf) {}
}
