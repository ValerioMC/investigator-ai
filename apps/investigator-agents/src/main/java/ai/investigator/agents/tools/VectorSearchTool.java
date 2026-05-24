package ai.investigator.agents.tools;

import ai.investigator.vector.repository.VectorRepository;
import dev.langchain4j.agent.tool.Tool;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class VectorSearchTool {

    private final VectorRepository vectorRepo;
    private final MeterRegistry metrics;

    public VectorSearchTool(VectorRepository vectorRepo, MeterRegistry metrics) {
        this.vectorRepo = vectorRepo;
        this.metrics = metrics;
    }

    @Tool("Perform semantic search on ingested documents (news articles, court records, " +
          "company filings, leaked documents). Returns the most relevant text passages " +
          "matching the query, along with their source and reliability.")
    public String searchDocuments(String query) {
        if (query == null || query.isBlank()) return "ERROR: query is required";
        var timer = Timer.start(metrics);
        try {
            metrics.counter("vector.tool.calls", "tool", "searchDocuments").increment();
            var results = vectorRepo.search(query, 5);
            if (results.isEmpty()) {
                return "No documents found for query: " + query;
            }
            return "DOCUMENT SEARCH RESULTS for: " + query + "\n" +
                results.stream()
                    .map(r -> "- [" + r.sourceType() + "] " + r.source() +
                        " (score: " + String.format("%.3f", r.score()) + ")" +
                        (r.entityIds() != null && !r.entityIds().isBlank()
                            ? " | entities: " + r.entityIds() : ""))
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "Document search unavailable: " + e.getMessage();
        } finally {
            timer.stop(metrics.timer("vector.tool.latency", "tool", "searchDocuments"));
        }
    }

    @Tool("Search for documents related to a specific company name. Useful for finding " +
          "news coverage, regulatory filings, or court cases involving the company.")
    public String searchDocumentsForCompany(String companyName) {
        if (companyName == null || companyName.isBlank()) return "ERROR: companyName is required";
        return searchDocuments("company: " + companyName);
    }

    @Tool("Search for documents related to a specific person's name. Returns relevant " +
          "passages from news, court records, or official documents.")
    public String searchDocumentsForPerson(String personName) {
        if (personName == null || personName.isBlank()) return "ERROR: personName is required";
        return searchDocuments("person: " + personName);
    }

    @Tool("Search specifically in court records and legal documents for mentions of " +
          "criminal proceedings, convictions, or investigations.")
    public String searchLegalDocuments(String query) {
        if (query == null || query.isBlank()) return "ERROR: query is required";
        var timer = Timer.start(metrics);
        try {
            metrics.counter("vector.tool.calls", "tool", "searchLegalDocuments").increment();
            var results = vectorRepo.search(query, 5, "court_records");
            if (results.isEmpty()) {
                results = vectorRepo.search("legal court crime conviction: " + query, 3);
            }
            if (results.isEmpty()) return "No legal documents found for: " + query;
            return "LEGAL DOCUMENTS for: " + query + "\n" +
                results.stream()
                    .map(r -> "- [COURT] " + r.source() +
                        " (score: " + String.format("%.3f", r.score()) + ")")
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "Legal document search unavailable: " + e.getMessage();
        } finally {
            timer.stop(metrics.timer("vector.tool.latency", "tool", "searchLegalDocuments"));
        }
    }
}
