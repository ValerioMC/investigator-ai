package ai.investigator.agents.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

@Component
public class DocumentAgentTool {

    private final VectorSearchTool vector;

    public DocumentAgentTool(VectorSearchTool vector) {
        this.vector = vector;
    }

    @Tool("Semantic search across ingested documents: news articles, court records, company filings, " +
          "and leaked documents. Returns matching text passages with source type and relevance score. " +
          "Only returns documents that exist in the vector store — no synthesis or inference. " +
          "Pass a person name, company name, or specific topic keyword.")
    public String searchDocuments(
            @P("Search term: a person name, company name, or specific topic, e.g. 'Luigi Conti court'") String query) {
        return vector.searchDocuments(query);
    }
}
