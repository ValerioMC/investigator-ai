package ai.investigator.api.resource;

import ai.investigator.agents.observability.LangfuseTraceContext;
import ai.investigator.agents.supervisor.InvestigationOrchestrator;
import ai.investigator.domain.report.EntityMap;
import ai.investigator.domain.report.InvestigationReport;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/v1")
public class InvestigationResource {

    private final InvestigationOrchestrator orchestrator;
    private final MeterRegistry metrics;
    // SNAKE_CASE to match the LLM's output field names (entity_map, recommended_follow_ups, agent_source)
    private final ObjectMapper mapper = new ObjectMapper()
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);

    private final AtomicLong investigationCount = new AtomicLong(0);

    public InvestigationResource(InvestigationOrchestrator orchestrator, MeterRegistry metrics) {
        this.orchestrator = orchestrator;
        this.metrics = metrics;
    }

    @PostMapping("/investigate")
    public ResponseEntity<InvestigationReport> investigate(@RequestBody InvestigateRequest request) {
        var timer = Timer.start(metrics);
        investigationCount.incrementAndGet();
        metrics.counter("investigations.total").increment();

        // Each /investigate call is its own Langfuse session — without this the
        // Sessions tab in Langfuse stays empty.
        LangfuseTraceContext.setSession(UUID.randomUUID().toString());
        try {
            String rawResponse = orchestrator.investigate(request.query(), request.focusEntities());

            InvestigationReport report = parseOrWrap(rawResponse, request.query());

            timer.stop(metrics.timer("investigations.latency"));
            metrics.counter("investigations.success").increment();
            return ResponseEntity.ok(report);

        } catch (Exception e) {
            metrics.counter("investigations.errors").increment();
            timer.stop(metrics.timer("investigations.latency"));
            throw e;
        } finally {
            LangfuseTraceContext.clear();
        }
    }

    @GetMapping("/metrics/investigation-stats")
    public ResponseEntity<InvestigationStats> investigationStats() {
        return ResponseEntity.ok(new InvestigationStats(investigationCount.get(), Instant.now().toString()));
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
