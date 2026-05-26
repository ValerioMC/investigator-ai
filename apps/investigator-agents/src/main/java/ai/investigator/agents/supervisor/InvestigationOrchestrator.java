package ai.investigator.agents.supervisor;

import ai.investigator.agents.tools.CorporateAgentTool;
import ai.investigator.agents.tools.DocumentAgentTool;
import ai.investigator.agents.tools.FinancialFlowAgentTool;
import ai.investigator.agents.tools.PersonProfileAgentTool;
import ai.investigator.agents.tools.SourceVerificationAgentTool;
import ai.investigator.graph.repository.Neo4jGraphRepository.ConflictEntry;
import ai.investigator.graph.repository.Neo4jGraphRepository.PathResult;
import ai.investigator.graph.service.GraphService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Java-driven orchestrator. No LLM reasoning during data collection — each step
 * is a deterministic graph/vector query. The LLM only sees the aggregated text
 * and is constrained (JSON response format + strict prompt) to map it into the
 * report structure without inventing facts.
 *
 * Flow:
 *   1. Resolve entities — union of user-supplied focusEntities and known graph
 *      entities whose name appears in the query text.
 *   2. Extract a date window from the query (defaults to wide-open).
 *   3. Run company tools for each company, person tools for each person.
 *   4. Cross-link section: for every (person, company) pair compute shortest
 *      path + a focused conflict check. This is what answers questions like
 *      "is there a conflict between X and contracts awarded to Y?".
 *   5. Date-bounded global conflict scan.
 *   6. Vector document search.
 *   7. Pass the bundle to SupervisorAgent for JSON synthesis.
 */
@Service
public class InvestigationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(InvestigationOrchestrator.class);

    // Suffixes used to bucket free-text entities the user typed without graph hits.
    private static final List<String> COMPANY_SUFFIXES =
        List.of("srl", "spa", "sa", "ltd", "gmbh", "inc", "bv", "nv", "llc", "plc", "corp", "offshore");

    private static final Pattern YEAR_RANGE = Pattern.compile("(19|20)(\\d{2})\\s*[-–/]\\s*(19|20)(\\d{2})");
    private static final Pattern SINGLE_YEAR = Pattern.compile("\\b(19|20)\\d{2}\\b");

    private final CorporateAgentTool corporateTool;
    private final PersonProfileAgentTool personTool;
    private final FinancialFlowAgentTool financialTool;
    private final DocumentAgentTool documentTool;
    private final SourceVerificationAgentTool verificationTool;
    private final SupervisorAgent supervisor;
    private final GraphService graph;

    public InvestigationOrchestrator(CorporateAgentTool corporateTool,
                                     PersonProfileAgentTool personTool,
                                     FinancialFlowAgentTool financialTool,
                                     DocumentAgentTool documentTool,
                                     SourceVerificationAgentTool verificationTool,
                                     SupervisorAgent supervisor,
                                     GraphService graph) {
        this.corporateTool    = corporateTool;
        this.personTool       = personTool;
        this.financialTool    = financialTool;
        this.documentTool     = documentTool;
        this.verificationTool = verificationTool;
        this.supervisor       = supervisor;
        this.graph            = graph;
    }

    public String investigate(String query, List<String> focusEntities) {
        log.info("Investigation start — query: {}, focusEntities: {}", query, focusEntities);

        Resolution resolved = resolveEntities(query, focusEntities);
        DateRange range = extractDateRange(query);
        log.info("Resolved persons={}, companies={}, dateRange={}",
            resolved.persons, resolved.companies, range);

        StringBuilder out = new StringBuilder();
        out.append("=== INVESTIGATION FINDINGS ===\n");
        out.append("ORIGINAL QUERY: ").append(query).append('\n');
        out.append("RESOLVED PERSONS: ").append(resolved.persons).append('\n');
        out.append("RESOLVED COMPANIES: ").append(resolved.companies).append('\n');
        out.append("DATE WINDOW: ").append(range.from).append(" → ").append(range.to).append("\n\n");

        // --- Per-company analysis ---
        for (String company : resolved.companies) {
            section(out, "CORPORATE ANALYSIS", company);
            out.append(corporateTool.analyzeCorporateOwnership(company)).append("\n\n");

            section(out, "FINANCIAL / CONTRACTS", company);
            out.append(financialTool.analyzeFinancials(company)).append("\n\n");
        }

        // --- Per-person analysis ---
        for (String person : resolved.persons) {
            section(out, "PERSON PROFILE", person);
            out.append(personTool.buildPersonProfile(person)).append("\n\n");
        }

        // --- Cross-link: person ↔ company ---
        for (String person : resolved.persons) {
            for (String company : resolved.companies) {
                section(out, "CROSS-LINK", person + " ↔ " + company);
                out.append(crossLink(person, company, range)).append("\n\n");
            }
        }

        // --- Date-bounded global conflict scan ---
        if (range.bounded()) {
            section(out, "CONFLICTS OF INTEREST IN WINDOW",
                range.from + " → " + range.to);
            out.append(scanConflictsInRange(range)).append("\n\n");
        }

        // --- Document evidence ---
        section(out, "DOCUMENT EVIDENCE", query);
        out.append(documentTool.searchDocuments(query)).append("\n\n");

        // --- Source verification on the primary claim ---
        if (!resolved.persons.isEmpty() && !resolved.companies.isEmpty()) {
            String claim = "Public officials connected to " + resolved.companies +
                " through " + resolved.persons + " during " + range.from + "–" + range.to;
            section(out, "SOURCE VERIFICATION", claim);
            out.append(verificationTool.verifyClaim(claim)).append("\n\n");
        }

        log.info("All tools executed. Passing to SupervisorAgent for JSON synthesis. " +
            "Bundle size: {} chars", out.length());
        String prompt = out.toString();
        return supervisor.investigate(prompt);
    }

    // ------------------------------------------------------------
    // Entity resolution
    // ------------------------------------------------------------

    record Resolution(Set<String> persons, Set<String> companies) {}

    private Resolution resolveEntities(String query, List<String> focusEntities) {
        Set<String> persons = new LinkedHashSet<>();
        Set<String> companies = new LinkedHashSet<>();

        // 1. Use explicit focus entities as the strongest signal.
        if (focusEntities != null) {
            for (String e : focusEntities) {
                if (e == null || e.isBlank()) continue;
                String clean = e.trim();
                if (looksLikeCompany(clean)) companies.add(clean);
                else persons.add(clean);
            }
        }

        // 2. Auto-resolve by scanning known graph entities for substring matches.
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
            log.warn("Entity auto-resolution failed (graph unavailable?): {}", e.getMessage());
        }

        return new Resolution(persons, companies);
    }

    private boolean looksLikeCompany(String entity) {
        String lower = entity.toLowerCase(Locale.ROOT);
        return COMPANY_SUFFIXES.stream()
            .anyMatch(s -> lower.endsWith(" " + s) || lower.endsWith(s) ||
                           lower.contains(" " + s + " ") || lower.contains(" " + s + "."));
    }

    // ------------------------------------------------------------
    // Date range
    // ------------------------------------------------------------

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
    // Cross-link & conflict scans
    // ------------------------------------------------------------

    private String crossLink(String person, String company, DateRange range) {
        StringBuilder sb = new StringBuilder();

        PathResult path = graph.personToCompanyPath(person, company);
        if (path == null) {
            sb.append("No graph path found between ").append(person)
              .append(" and ").append(company).append(".\n");
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
            sb.append("DIRECT CONFLICT(S) DETECTED:\n");
            for (ConflictEntry c : matching) {
                sb.append("- ").append(c.publicBody())
                  .append(" issued contract \"").append(c.contract())
                  .append("\" awarded to ").append(c.company())
                  .append(" (€").append(String.format("%,.0f", c.amount())).append(")");
                if (c.awardedAt() != null) {
                    sb.append(" on ").append(c.awardedAt());
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private String scanConflictsInRange(DateRange range) {
        List<ConflictEntry> conflicts = graph.detectConflictsInRange(range.from, range.to);
        if (conflicts.isEmpty()) {
            return "No conflicts of interest detected within window.";
        }
        return conflicts.stream()
            .map(c -> "- " + c.person() + " held role at [" + c.publicBody() + "] " +
                "while contract \"" + c.contract() + "\" was awarded to [" + c.company() + "]" +
                " (€" + String.format("%,.0f", c.amount()) + ")" +
                (c.awardedAt() != null ? " on " + c.awardedAt() : ""))
            .collect(Collectors.joining("\n"));
    }

    // ------------------------------------------------------------
    // Formatting helpers
    // ------------------------------------------------------------

    private void section(StringBuilder out, String title, String subject) {
        out.append("--- ").append(title).append(": ").append(subject).append(" ---\n");
    }
}
