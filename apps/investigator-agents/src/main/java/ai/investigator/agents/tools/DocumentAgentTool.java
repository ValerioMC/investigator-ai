package ai.investigator.agents.tools;

import ai.investigator.agents.document.DocumentAgent;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

@Component
public class DocumentAgentTool {

    private final DocumentAgent documentAgent;

    public DocumentAgentTool(DocumentAgent documentAgent) {
        this.documentAgent = documentAgent;
    }

    @Tool("Search and synthesize information from unstructured documents: news articles, " +
          "court records, company filings, and leaked documents. " +
          "Use to find documentary evidence supporting or contradicting a claim.")
    public String searchDocuments(
            @P("Query describing what documentary evidence to find, e.g. 'Court records mentioning Luigi Conti'")
            String query) {
        return documentAgent.searchAndSynthesize(query);
    }
}
