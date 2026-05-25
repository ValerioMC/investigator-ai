package ai.investigator.agents.tools;

import ai.investigator.graph.repository.Neo4jGraphRepository.ConflictEntry;
import ai.investigator.graph.repository.Neo4jGraphRepository.ConvictionEntry;
import ai.investigator.graph.repository.Neo4jGraphRepository.OwnershipEntry;
import ai.investigator.graph.service.GraphService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class GraphTraversalTool {

    private static final Logger log = LoggerFactory.getLogger(GraphTraversalTool.class);

    private final GraphService graph;
    private final MeterRegistry metrics;

    public GraphTraversalTool(GraphService graph, MeterRegistry metrics) {
        this.graph = graph;
        this.metrics = metrics;
    }

    @Tool("Find all companies owned or controlled by a person, including indirect ownership " +
          "through intermediate companies. Returns company names, jurisdictions, and legal forms.")
    public String findCompaniesByPerson(@P("Full legal name of the person") String personFullName) {
        if (personFullName == null || personFullName.isBlank())
            return "ERROR: personFullName is required";
        log.warn("[TOOL] findCompaniesByPerson called with: '{}'", personFullName);
        var timer = Timer.start(metrics);
        try {
            var companies = graph.findCompaniesByPerson(personFullName);
            metrics.counter("graph.tool.calls", "tool", "findCompaniesByPerson").increment();
            if (companies.isEmpty()) {
                log.warn("[TOOL] findCompaniesByPerson returned empty for: '{}'", personFullName);
                return "No companies found for " + personFullName;
            }
            String result = "COMPANIES CONTROLLED BY " + personFullName + ":\n" +
                companies.stream()
                    .map(c -> "- " + c.name() + " (" + c.legalForm() + ", " +
                        c.jurisdiction() + (c.active() ? "" : ", INACTIVE") + ")")
                    .collect(Collectors.joining("\n"));
            log.warn("[TOOL] findCompaniesByPerson result: {}", result);
            return result;
        } finally {
            timer.stop(metrics.timer("graph.tool.latency", "tool", "findCompaniesByPerson"));
        }
    }

    @Tool("Find the Ultimate Beneficial Owner (UBO) of a company by traversing the ownership " +
          "chain up to 5 hops. Returns the natural persons who ultimately control the company.")
    public String findUBO(@P("Full legal name of the company") String companyName) {
        if (companyName == null || companyName.isBlank()) return "ERROR: companyName is required";
        var timer = Timer.start(metrics);
        try {
            var persons = graph.findUBO(companyName);
            metrics.counter("graph.tool.calls", "tool", "findUBO").increment();
            if (persons.isEmpty()) {
                String r = "No UBO found for " + companyName + " — ownership chain may be opaque.";
                log.warn("[TOOL] findUBO({}) → {}", companyName, r);
                return r;
            }
            String result = "ULTIMATE BENEFICIAL OWNERS of " + companyName + ":\n" +
                persons.stream()
                    .map(p -> "- " + p.fullName() +
                        (p.nationality() != null ? " (" + p.nationality() + ")" : "") +
                        (p.politicalRole() != null ? " [PUBLIC ROLE: " + p.politicalRole() + "]" : "") +
                        (p.riskScore() != null && p.riskScore() > 0.6 ? " [HIGH RISK]" : ""))
                    .collect(Collectors.joining("\n"));
            log.warn("[TOOL] findUBO({}) → {}", companyName, result);
            return result;
        } finally {
            timer.stop(metrics.timer("graph.tool.latency", "tool", "findUBO"));
        }
    }

    @Tool("Get the full ownership chain of a company: who owns what percentage, " +
          "directly or indirectly.")
    public String findOwnershipChain(@P("Full legal name of the company") String companyName) {
        if (companyName == null || companyName.isBlank()) return "ERROR: companyName is required";
        var timer = Timer.start(metrics);
        try {
            var entries = graph.findOwnershipChain(companyName);
            metrics.counter("graph.tool.calls", "tool", "findOwnershipChain").increment();
            if (entries.isEmpty()) {
                String r = "No ownership data found for " + companyName;
                log.warn("[TOOL] findOwnershipChain({}) → {}", companyName, r);
                return r;
            }
            StringBuilder sb = new StringBuilder("OWNERSHIP CHAIN for ").append(companyName).append(":\n");
            double knownShares = 0;
            for (OwnershipEntry e : entries) {
                sb.append("- ").append(e.ownerName())
                    .append(" → OWNS ").append(String.format("%.1f%%", e.sharePercent()))
                    .append(" (").append(e.ownershipType().toLowerCase()).append(", ")
                    .append(e.nationality()).append(")\n");
                knownShares += e.sharePercent();
            }
            double unknown = 100.0 - knownShares;
            if (unknown > 0) {
                sb.append("- Remaining ").append(String.format("%.1f%%", unknown))
                    .append(": unknown / bearer shares\n");
            }
            log.warn("[TOOL] findOwnershipChain({}) → {}", companyName, sb);
            return sb.toString();
        } finally {
            timer.stop(metrics.timer("graph.tool.latency", "tool", "findOwnershipChain"));
        }
    }

    @Tool("Detect potential conflicts of interest: find persons who held a public role at a " +
          "body that issued contracts, and also owned or directed companies that won those " +
          "contracts (or whose family members did). Provide date range as ISO-8601.")
    public String detectConflictOfInterest(
            @P("Full name of person to check, or null to scan all") String personName,
            @P("Start date ISO-8601 e.g. 2018-01-01, or null") String dateFrom,
            @P("End date ISO-8601 e.g. 2024-12-31, or null") String dateTo) {
        var timer = Timer.start(metrics);
        try {
            metrics.counter("graph.tool.calls", "tool", "detectConflictOfInterest").increment();
            List<ConflictEntry> conflicts;
            if (personName != null && !personName.isBlank()) {
                conflicts = graph.detectConflictsForPerson(personName);
            } else {
                conflicts = graph.detectConflictsOfInterest(dateFrom, dateTo);
            }
            if (conflicts.isEmpty()) {
                String r = "No conflicts of interest detected" + (personName != null ? " for " + personName : "") + ".";
                log.warn("[TOOL] detectConflictOfInterest({}) → {}", personName, r);
                return r;
            }
            String result = "CONFLICTS OF INTEREST DETECTED:\n" +
                conflicts.stream().map(c ->
                    "- " + c.person() + " held role at [" + c.publicBody() + "] " +
                    "while their company [" + c.company() + "] won contract: " +
                    c.contract() + " (€" + String.format("%,.0f", c.amount()) + ")"
                ).collect(Collectors.joining("\n"));
            log.warn("[TOOL] detectConflictOfInterest({}) → {}", personName, result);
            return result;
        } finally {
            timer.stop(metrics.timer("graph.tool.latency", "tool", "detectConflictOfInterest"));
        }
    }

    @Tool("Find all connections of a person or company to tax havens (offshore jurisdictions " +
          "classified as tax havens by FATF/OECD).")
    public String findTaxHavenConnections(@P("Full name of the person") String personName) {
        if (personName == null || personName.isBlank()) return "ERROR: personName is required";
        var timer = Timer.start(metrics);
        try {
            var entries = graph.findTaxHavenConnections(personName);
            metrics.counter("graph.tool.calls", "tool", "findTaxHavenConnections").increment();
            if (entries.isEmpty()) {
                return "No direct tax haven connections found for " + personName;
            }
            return "TAX HAVEN CONNECTIONS for " + personName + ":\n" +
                entries.stream()
                    .map(e -> "- " + e.company() + " registered in " +
                        e.jurisdiction() + " [" + e.isoCode() + "] [TAX HAVEN]")
                    .collect(Collectors.joining("\n"));
        } finally {
            timer.stop(metrics.timer("graph.tool.latency", "tool", "findTaxHavenConnections"));
        }
    }

    @Tool("Find the criminal record of a person: convictions, pending investigations, acquittals.")
    public String findConvictions(@P("Full name of the person") String personName) {
        if (personName == null || personName.isBlank()) return "ERROR: personName is required";
        var timer = Timer.start(metrics);
        try {
            var convictions = graph.findConvictions(personName);
            metrics.counter("graph.tool.calls", "tool", "findConvictions").increment();
            if (convictions.isEmpty()) {
                return "No criminal record found for " + personName;
            }
            return "CRIMINAL RECORD for " + personName + ":\n" +
                convictions.stream().map(c ->
                    "- [" + c.status() + "] " + c.type() + " (" + c.severity() + ") " +
                    "in " + c.year() + " at " + c.court() + ": " + c.description()
                ).collect(Collectors.joining("\n"));
        } finally {
            timer.stop(metrics.timer("graph.tool.latency", "tool", "findConvictions"));
        }
    }

    @Tool("Find the family network of a person: spouse, children, parents, siblings. " +
          "Useful for detecting proxy ownership through relatives.")
    public String findFamilyNetwork(@P("Full name of the person") String personName) {
        if (personName == null || personName.isBlank()) return "ERROR: personName is required";
        var relatives = graph.findFamilyNetwork(personName);
        metrics.counter("graph.tool.calls", "tool", "findFamilyNetwork").increment();
        if (relatives.isEmpty()) return "No family relations found for " + personName;
        return "FAMILY NETWORK of " + personName + ":\n" +
            relatives.stream()
                .map(r -> "- " + r.name() + " (" + r.relationshipType() + ")")
                .collect(Collectors.joining("\n"));
    }

    @Tool("Find the shortest connection path between two named persons in the graph. " +
          "Traverses up to 6 hops across any relationship type.")
    public String findShortestPath(
            @P("Full name of the first person") String person1,
            @P("Full name of the second person") String person2) {
        if (person1 == null || person1.isBlank() || person2 == null || person2.isBlank())
            return "ERROR: both person1 and person2 are required";
        var result = graph.shortestPath(person1, person2);
        metrics.counter("graph.tool.calls", "tool", "findShortestPath").increment();
        if (result == null) return "No connection found between " + person1 + " and " + person2;
        return "CONNECTION PATH (" + result.hops() + " hops): " +
            String.join(" → ", result.nodeNames());
    }

    @Tool("Find all public contracts won by a company, including amounts and issuing bodies.")
    public String findContractsWonByCompany(@P("Full legal name of the company") String companyName) {
        if (companyName == null || companyName.isBlank()) return "ERROR: companyName is required";
        var contracts = graph.findContractsWonByCompany(companyName);
        metrics.counter("graph.tool.calls", "tool", "findContractsWonByCompany").increment();
        if (contracts.isEmpty()) return "No contracts found for " + companyName;
        double total = contracts.stream().mapToDouble(c -> c.amount()).sum();
        return "CONTRACTS WON BY " + companyName + " (total: €" +
            String.format("%,.0f", total) + "):\n" +
            contracts.stream()
                .map(c -> "- " + c.title() + ": €" + String.format("%,.0f", c.amount()) +
                    " from " + c.issuedBy() +
                    (c.awardedAt() != null ? " (" + c.awardedAt() + ")" : ""))
                .collect(Collectors.joining("\n"));
    }

    @Tool("Find all source documents (news articles, court records, filings) that mention " +
          "a given person in the graph.")
    public String findDocumentsForPerson(@P("Full name of the person") String personName) {
        if (personName == null || personName.isBlank()) return "ERROR: personName is required";
        var docs = graph.findDocumentsForPerson(personName);
        metrics.counter("graph.tool.calls", "tool", "findDocumentsForPerson").increment();
        if (docs.isEmpty()) return "No documents found for " + personName;
        return "DOCUMENTS MENTIONING " + personName + ":\n" +
            docs.stream()
                .map(d -> "- [" + d.sourceType() + "] " + d.title() +
                    " (reliability: " + d.reliability() + ")" +
                    (d.context() != null && !d.context().isBlank() ? ": " + d.context() : ""))
                .collect(Collectors.joining("\n"));
    }
}
