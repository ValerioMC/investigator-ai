package ai.investigator.agents.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DocumentAgentTool {

    private static final Logger log = LoggerFactory.getLogger(DocumentAgentTool.class);

    private final VectorSearchTool vector;

    public DocumentAgentTool(VectorSearchTool vector) {
        this.vector = vector;
    }

    @Tool("Search and synthesize information from unstructured documents: news articles, " +
          "court records, company filings, and leaked documents. " +
          "Use to find documentary evidence supporting or contradicting a claim.")
    public String searchDocuments(
            @P("Query describing what documentary evidence to find, e.g. 'Court records mentioning Luigi Conti'")
            String query) {
        log.warn("[DOCUMENT-TOOL] called for: {}", query);
        String result = vector.searchDocuments(query);
        log.warn("[DOCUMENT-TOOL] result: {}", result);
        return result;
    }
}
