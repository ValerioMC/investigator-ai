package ai.investigator.api.resource;

import ai.investigator.agents.supervisor.SupervisorAgent;
import ai.investigator.domain.report.EntityMap;
import ai.investigator.domain.report.InvestigationReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/v1")
public class InvestigationResource {

    private final SupervisorAgent supervisor;
    private final MeterRegistry metrics;
    private final ObjectMapper mapper = new ObjectMapper();

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

        try {
            String focusHint = request.focusEntities().isEmpty() ? "" :
                " Focus on: " + String.join(", ", request.focusEntities()) + ".";
            String prompt = request.query() + focusHint +
                " Investigation depth: " + request.depth() + " hops.";

            String rawResponse = supervisor.investigate(prompt);

            InvestigationReport report = parseOrWrap(rawResponse, request.query());

            timer.stop(metrics.timer("investigations.latency"));
            metrics.counter("investigations.success").increment();
            return ResponseEntity.ok(report);

        } catch (Exception e) {
            metrics.counter("investigations.errors").increment();
            timer.stop(metrics.timer("investigations.latency"));
            throw e;
        }
    }

    @GetMapping("/metrics/investigation-stats")
    public ResponseEntity<InvestigationStats> investigationStats() {
        return ResponseEntity.ok(new InvestigationStats(investigationCount.get(), Instant.now().toString()));
    }

    private InvestigationReport parseOrWrap(String raw, String originalQuery) {
        try {
            String json = raw.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```[a-z]*\n?", "").replaceAll("\n?```$", "").trim();
            }
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
