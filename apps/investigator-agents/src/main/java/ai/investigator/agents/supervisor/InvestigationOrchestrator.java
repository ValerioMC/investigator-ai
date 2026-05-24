package ai.investigator.agents.supervisor;

import ai.investigator.agents.observability.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.UUID;

/**
 * Two-step design:
 *   Step 1 — SupervisorAgent is the only LLM. Tools call Neo4j/Qdrant directly with no
 *             intermediate LLM hop. The model is constrained to report only what tools return.
 *   Step 2 — ReportFormatter receives the plain-text findings and produces the final JSON.
 *             It has no tools, so it cannot hallucinate tool output.
 */
@Service
public class InvestigationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(InvestigationOrchestrator.class);

    private final SupervisorAgent  supervisor;
    private final ReportFormatter  formatter;

    public InvestigationOrchestrator(SupervisorAgent supervisor, ReportFormatter formatter) {
        this.supervisor = supervisor;
        this.formatter  = formatter;
    }

    public String investigate(String query) {
        log.info("Investigation start: {}", query);
        // All LLM calls on this thread share the same Langfuse trace — supervisor +
        // every sub-agent appear as child generations under one trace in the UI.
        TraceContext.set(UUID.randomUUID().toString(), query);
        try {
            String findings = supervisor.investigate(query);
            log.info("Supervisor done ({} chars), formatting report", findings.length());
            return formatter.toJson(query, findings);
        } finally {
            TraceContext.clear();
        }
    }
}
