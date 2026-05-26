package ai.investigator.agents.supervisor;

import ai.investigator.agents.corporate.CorporateAgent;
import ai.investigator.agents.document.DocumentAgent;
import ai.investigator.agents.financial.FinancialFlowAgent;
import ai.investigator.agents.observability.LangfuseClient;
import ai.investigator.agents.observability.LangfuseTraceContext;
import ai.investigator.agents.person.PersonProfileAgent;
import ai.investigator.agents.tools.CorporateAgentTool;
import ai.investigator.agents.tools.DocumentAgentTool;
import ai.investigator.agents.tools.FinancialFlowAgentTool;
import ai.investigator.agents.tools.PersonProfileAgentTool;
import ai.investigator.agents.tools.SourceVerificationAgentTool;
import ai.investigator.agents.verification.SourceVerificationAgent;
import ai.investigator.graph.projection.ConflictEntry;
import ai.investigator.graph.projection.PathResult;
import ai.investigator.graph.service.GraphService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Multi-agent orchestrator.
 *
 * For each investigation a single umbrella trace is opened in Langfuse. Each
 * step (Corporate, Person, Financial, Document, Verification, Supervisor) is
 * wrapped in a named span; the LLM call inside each step attaches as a child
 * {@code generation} of that span. The result in the Langfuse UI is a real
 * orchestration tree: Investigation → step-corporate → CorporateAgent, etc.
 *
 * Data collection is fully deterministic Java (the *Tool classes). Subagents
 * have no tools — they only synthesize the pre-collected payload into a JSON
 * AgentReport. The Supervisor merges those reports into the final report.
 */
@Service
public class InvestigationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(InvestigationOrchestrator.class);

    private static final List<String> COMPANY_SUFFIXES =
        List.of("srl", "spa", "sa", "ltd", "gmbh", "inc", "bv", "nv", "llc", "plc", "corp", "offshore");

    private static final Pattern YEAR_RANGE = Pattern.compile("(19|20)(\\d{2})\\s*[-–/]\\s*(19|20)(\\d{2})");
    private static final Pattern SINGLE_YEAR = Pattern.compile("\\b(19|20)\\d{2}\\b");

    // Data collectors (deterministic Java — no LLM)
    private final CorporateAgentTool corporateData;
    private final PersonProfileAgentTool personData;
    private final FinancialFlowAgentTool financialData;
    private final DocumentAgentTool documentData;
    private final SourceVerificationAgentTool verificationData;

    // Subagents (LLM synthesis layer — no tools)
    private final CorporateAgent corporateAgent;
    private final PersonProfileAgent personAgent;
    private final FinancialFlowAgent financialAgent;
    private final DocumentAgent documentAgent;
    private final SourceVerificationAgent verificationAgent;
    private final SupervisorAgent supervisor;

    private final GraphService graph;

    // Optional — only present when langfuse.enabled=true
    private final Optional<LangfuseClient> langfuse;

    @Autowired
    public InvestigationOrchestrator(CorporateAgentTool corporateData,
                                     PersonProfileAgentTool personData,
                                     FinancialFlowAgentTool financialData,
                                     DocumentAgentTool documentData,
                                     SourceVerificationAgentTool verificationData,
                                     CorporateAgent corporateAgent,
                                     PersonProfileAgent personAgent,
                                     FinancialFlowAgent financialAgent,
                                     DocumentAgent documentAgent,
                                     SourceVerificationAgent verificationAgent,
                                     SupervisorAgent supervisor,
                                     GraphService graph,
                                     Optional<LangfuseClient> langfuse) {
        this.corporateData      = corporateData;
        this.personData         = personData;
        this.financialData      = financialData;
        this.documentData       = documentData;
        this.verificationData   = verificationData;
        this.corporateAgent     = corporateAgent;
        this.personAgent        = personAgent;
        this.financialAgent     = financialAgent;
        this.documentAgent      = documentAgent;
        this.verificationAgent  = verificationAgent;
        this.supervisor         = supervisor;
        this.graph              = graph;
        this.langfuse           = langfuse;
    }

    public String investigate(String query, List<String> focusEntities) {
        log.info("Investigation start — query: {}, focusEntities: {}", query, focusEntities);

        // -------- Phase 0: resolution (Java only) --------
        Resolution resolved = resolveEntities(query, focusEntities);
        DateRange range = extractDateRange(query);
        log.info("Resolved persons={}, companies={}, dateRange={}",
            resolved.persons, resolved.companies, range);

        // -------- Phase 1: open umbrella trace --------
        String traceId = UUID.randomUUID().toString();
        LangfuseTraceContext.setParentTrace(traceId);
        openTrace(traceId, query, resolved, range);

        try {
            // -------- Phase 2: per-domain steps --------
            String corporateReport    = step("step-corporate",    traceId, "CorporateAgent",
                () -> buildCorporatePayload(query, resolved),
                corporateAgent::synthesize);

            String personReport       = step("step-person",       traceId, "PersonProfileAgent",
                () -> buildPersonPayload(query, resolved),
                personAgent::synthesize);

            String financialReport    = step("step-financial",    traceId, "FinancialFlowAgent",
                () -> buildFinancialPayload(query, resolved),
                financialAgent::synthesize);

            String documentReport     = step("step-document",     traceId, "DocumentAgent",
                () -> buildDocumentPayload(query),
                documentAgent::synthesize);

            String verificationReport = step("step-verification", traceId, "SourceVerificationAgent",
                () -> buildVerificationPayload(query, resolved, range),
                verificationAgent::synthesize);

            // -------- Phase 3: final supervisor merge --------
            StringBuilder bundle = new StringBuilder();
            bundle.append("ORIGINAL QUERY: ").append(query).append("\n\n");
            bundle.append("SUBAGENT REPORTS (in order):\n\n");
            appendReport(bundle, "CorporateAgent",          corporateReport);
            appendReport(bundle, "PersonProfileAgent",      personReport);
            appendReport(bundle, "FinancialFlowAgent",      financialReport);
            appendReport(bundle, "DocumentAgent",           documentReport);
            appendReport(bundle, "SourceVerificationAgent", verificationReport);

            String finalReport = step("step-supervisor", traceId, "SupervisorAgent",
                bundle::toString,
                supervisor::investigate);

            // -------- Phase 4: close umbrella trace --------
            closeTrace(traceId, finalReport);
            return finalReport;

        } finally {
            LangfuseTraceContext.setParentTrace(null);
            LangfuseTraceContext.setParentObservation(null);
        }
    }

    // ------------------------------------------------------------
    // Step runner — wraps a (collect → synthesize) pair in a Langfuse span
    // ------------------------------------------------------------

    private String step(String spanName, String traceId, String agentName,
                        java.util.function.Supplier<String> collector,
                        Function<String, String> agent) {
        Instant t0 = Instant.now();
        String spanId = UUID.randomUUID().toString();

        // Collect payload first so it can be sent as span input (data the agent will see).
        String payload;
        try {
            payload = collector.get();
        } catch (Exception e) {
            log.warn("{} payload collection failed: {}", spanName, e.getMessage());
            payload = "ERROR collecting payload: " + e.getMessage();
        }

        openSpan(traceId, spanId, spanName, t0, payload);
        LangfuseTraceContext.setParentObservation(spanId);

        String out;
        String level = null;
        try {
            out = agent.apply(payload);
            log.info("{} done in {} ms — {} chars",
                agentName, java.time.Duration.between(t0, Instant.now()).toMillis(),
                out != null ? out.length() : 0);
        } catch (Exception e) {
            log.warn("{} failed: {}", agentName, e.getMessage());
            out = emptyAgentReport(agentName, "subagent call failed: " + e.getMessage());
            level = "ERROR";
        } finally {
            LangfuseTraceContext.setParentObservation(null);
        }

        closeSpan(traceId, spanId, out, level);
        return out;
    }

    // ------------------------------------------------------------
    // Langfuse span helpers (no-ops if observability is disabled)
    // ------------------------------------------------------------

    private void openTrace(String traceId, String query, Resolution resolved, DateRange range) {
        langfuse.ifPresent(c -> {
            String session = LangfuseTraceContext.session();
            String user    = LangfuseTraceContext.user();
            c.postEvents(List.of(
                c.traceCreate(traceId,
                    "Investigation",
                    session, user,
                    query,
                    List.of("investigator-ai", "investigation"))
            ));
            // Add a brief input span describing the resolved scope so the tree
            // also shows the deterministic Java phase.
            String scopeSpanId = UUID.randomUUID().toString();
            Instant now = Instant.now();
            String scopeInput = "persons=" + resolved.persons + "\n" +
                                "companies=" + resolved.companies + "\n" +
                                "dateWindow=" + range.from + " → " + range.to;
            c.postEvents(List.of(
                c.spanCreate(scopeSpanId, traceId, null,
                    "resolve-entities-and-window",
                    now,
                    scopeInput,
                    Map.of("phase", "java-deterministic"))
            ));
            c.postEvents(List.of(
                c.spanUpdate(scopeSpanId, traceId, now, "OK", null)
            ));
        });
    }

    private void closeTrace(String traceId, String finalReport) {
        langfuse.ifPresent(c -> c.postEvents(List.of(
            c.traceUpdate(traceId, finalReport)
        )));
    }

    private void openSpan(String traceId, String spanId, String name,
                          Instant start, String input) {
        langfuse.ifPresent(c -> c.postEvents(List.of(
            c.spanCreate(spanId, traceId, null, name, start, input,
                Map.of("phase", "subagent-synthesis"))
        )));
    }

    private void closeSpan(String traceId, String spanId, String output, String level) {
        langfuse.ifPresent(c -> c.postEvents(List.of(
            c.spanUpdate(spanId, traceId, Instant.now(), output, level)
        )));
    }

    // ------------------------------------------------------------
    // Payload builders — deterministic, no LLM
    // ------------------------------------------------------------

    private String buildCorporatePayload(String query, Resolution resolved) {
        StringBuilder sb = new StringBuilder();
        sb.append("QUERY: ").append(query).append('\n');
        sb.append("COMPANIES UNDER ANALYSIS: ").append(resolved.companies).append("\n\n");
        if (resolved.companies.isEmpty()) {
            sb.append("No companies resolved from the query.\n");
        } else {
            for (String company : resolved.companies) {
                sb.append("=== ").append(company).append(" ===\n");
                sb.append(corporateData.analyzeCorporateOwnership(company)).append("\n\n");
            }
        }
        return sb.toString();
    }

    private String buildPersonPayload(String query, Resolution resolved) {
        StringBuilder sb = new StringBuilder();
        sb.append("QUERY: ").append(query).append('\n');
        sb.append("PERSONS UNDER ANALYSIS: ").append(resolved.persons).append("\n\n");
        if (resolved.persons.isEmpty()) {
            sb.append("No persons resolved from the query.\n");
        } else {
            for (String person : resolved.persons) {
                sb.append("=== ").append(person).append(" ===\n");
                sb.append(personData.buildPersonProfile(person)).append("\n\n");
            }
        }
        return sb.toString();
    }

    private String buildFinancialPayload(String query, Resolution resolved) {
        StringBuilder sb = new StringBuilder();
        sb.append("QUERY: ").append(query).append('\n');
        sb.append("COMPANIES UNDER ANALYSIS: ").append(resolved.companies).append("\n\n");
        if (resolved.companies.isEmpty()) {
            sb.append("No companies resolved from the query.\n");
        } else {
            for (String company : resolved.companies) {
                sb.append("=== ").append(company).append(" ===\n");
                sb.append(financialData.analyzeFinancials(company)).append("\n\n");
            }
        }
        return sb.toString();
    }

    private String buildDocumentPayload(String query) {
        StringBuilder sb = new StringBuilder();
        sb.append("QUERY: ").append(query).append("\n\n");
        sb.append(documentData.searchDocuments(query)).append('\n');
        return sb.toString();
    }

    private String buildVerificationPayload(String query, Resolution resolved, DateRange range) {
        StringBuilder sb = new StringBuilder();
        sb.append("QUERY: ").append(query).append('\n');
        sb.append("DATE WINDOW: ").append(range.from).append(" → ").append(range.to).append("\n\n");

        for (String person : resolved.persons) {
            for (String company : resolved.companies) {
                sb.append("--- CROSS-LINK: ").append(person).append(" ↔ ").append(company).append(" ---\n");
                sb.append(crossLink(person, company)).append("\n\n");
            }
        }
        if (range.bounded()) {
            sb.append("--- CONFLICTS OF INTEREST IN WINDOW ---\n");
            sb.append(scanConflictsInRange(range)).append("\n\n");
        }
        if (!resolved.persons.isEmpty() && !resolved.companies.isEmpty()) {
            String claim = "Public officials connected to " + resolved.companies +
                " through " + resolved.persons + " during " + range.from + "–" + range.to;
            sb.append("--- DOCUMENT EVIDENCE FOR CLAIM ---\n");
            sb.append("CLAIM: ").append(claim).append('\n');
            sb.append(verificationData.verifyClaim(claim)).append('\n');
        }
        return sb.toString();
    }

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------

    private String emptyAgentReport(String agentName, String reason) {
        return "{\"agent_source\":\"" + agentName + "\"," +
               "\"findings\":[{\"claim\":\"" + reason.replace("\"", "'") +
               "\",\"confidence\":\"UNVERIFIED\",\"evidence\":[]}]," +
               "\"entities\":{\"persons\":[],\"companies\":[],\"contracts\":[]}}";
    }

    private void appendReport(StringBuilder sb, String name, String json) {
        sb.append("--- ").append(name).append(" REPORT ---\n");
        sb.append(json).append("\n\n");
    }

    // ------------------------------------------------------------
    // Entity / date resolution
    // ------------------------------------------------------------

    record Resolution(Set<String> persons, Set<String> companies) {}

    private Resolution resolveEntities(String query, List<String> focusEntities) {
        Set<String> persons = new LinkedHashSet<>();
        Set<String> companies = new LinkedHashSet<>();

        if (focusEntities != null) {
            for (String e : focusEntities) {
                if (e == null || e.isBlank()) continue;
                String clean = e.trim();
                if (looksLikeCompany(clean)) companies.add(clean);
                else persons.add(clean);
            }
        }

        String q = query == null ? "" : query.toLowerCase(Locale.ROOT);
        try {
            for (String name : graph.listAllCompanyNames()) {
                if (name == null) continue;
                if (q.contains(name.toLowerCase(Locale.ROOT))) companies.add(name);
            }
            for (String name : graph.listAllPersonNames()) {
                if (name == null) continue;
                if (q.contains(name.toLowerCase(Locale.ROOT))) persons.add(name);
            }
        } catch (Exception e) {
            log.warn("Entity auto-resolution failed: {}", e.getMessage());
        }
        return new Resolution(persons, companies);
    }

    private boolean looksLikeCompany(String entity) {
        String lower = entity.toLowerCase(Locale.ROOT);
        return COMPANY_SUFFIXES.stream()
            .anyMatch(s -> lower.endsWith(" " + s) || lower.endsWith(s) ||
                           lower.contains(" " + s + " ") || lower.contains(" " + s + "."));
    }

    record DateRange(String from, String to) {
        boolean bounded() {
            return !"1900-01-01".equals(from) || !"2100-12-31".equals(to);
        }
    }

    DateRange extractDateRange(String query) {
        if (query == null) return new DateRange("1900-01-01", "2100-12-31");
        Matcher m = YEAR_RANGE.matcher(query);
        if (m.find()) {
            String y1 = m.group(1) + m.group(2);
            String y2 = m.group(3) + m.group(4);
            return new DateRange(y1 + "-01-01", y2 + "-12-31");
        }
        Matcher single = SINGLE_YEAR.matcher(query);
        if (single.find()) {
            String y = single.group();
            return new DateRange(y + "-01-01", y + "-12-31");
        }
        return new DateRange("1900-01-01", "2100-12-31");
    }

    // ------------------------------------------------------------
    // Cross-link / conflict helpers
    // ------------------------------------------------------------

    private String crossLink(String person, String company) {
        StringBuilder sb = new StringBuilder();
        PathResult path = graph.personToCompanyPath(person, company);
        if (path == null) {
            sb.append("No graph path between ").append(person).append(" and ").append(company).append(".\n");
        } else {
            sb.append("CONNECTION PATH (").append(path.hops()).append(" hops): ")
              .append(String.join(" → ", path.nodeNames())).append('\n');
        }

        List<ConflictEntry> personConflicts = graph.detectConflictsForPerson(person);
        List<ConflictEntry> matching = personConflicts.stream()
            .filter(c -> company.equalsIgnoreCase(c.company()))
            .toList();
        if (matching.isEmpty()) {
            sb.append("No direct contract-level conflict between ").append(person)
              .append(" and ").append(company).append(".");
        } else {
            sb.append("DIRECT CONFLICT(S):\n");
            for (ConflictEntry c : matching) {
                sb.append("- ").append(c.publicBody())
                  .append(" issued contract \"").append(c.contract())
                  .append("\" awarded to ").append(c.company())
                  .append(" (€").append(String.format("%,.0f", c.amount())).append(")");
                if (c.awardedAt() != null) sb.append(" on ").append(c.awardedAt());
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private String scanConflictsInRange(DateRange range) {
        List<ConflictEntry> conflicts = graph.detectConflictsInRange(range.from, range.to);
        if (conflicts.isEmpty()) return "No conflicts of interest detected within window.";
        return conflicts.stream()
            .map(c -> "- " + c.person() + " held role at [" + c.publicBody() + "] " +
                "while contract \"" + c.contract() + "\" was awarded to [" + c.company() + "]" +
                " (€" + String.format("%,.0f", c.amount()) + ")" +
                (c.awardedAt() != null ? " on " + c.awardedAt() : ""))
            .collect(Collectors.joining("\n"));
    }
}
