package ai.investigator.agents.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SourceVerificationAgentTool {

    private static final Logger log = LoggerFactory.getLogger(SourceVerificationAgentTool.class);

    private final GraphTraversalTool graph;
    private final VectorSearchTool vector;

    public SourceVerificationAgentTool(GraphTraversalTool graph, VectorSearchTool vector) {
        this.graph = graph;
        this.vector = vector;
    }

    @Tool("Cross-reference a specific claim against graph data and documents. " +
          "Returns evidence found in the database. Confidence must be assigned by the calling agent based on what is returned.")
    public String verifyClaim(
            @P("The specific claim to verify, phrased as a single sentence") String claim) {
        log.warn("[VERIFY-TOOL] verifying claim: {}", claim);
        String docs;
        try {
            docs = vector.searchDocuments(claim);
        } catch (Exception e) {
            log.error("[VERIFY-TOOL] searchDocuments threw {}: {}", e.getClass().getSimpleName(), e.getMessage(), e);
            docs = "searchDocuments unavailable (" + e.getClass().getSimpleName() + ")";
        }
        String result = "DOCUMENT EVIDENCE:\n" + docs;
        log.warn("[VERIFY-TOOL] result: {}", result);
        return result;
    }
}
