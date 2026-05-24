package ai.investigator.agents.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

@Component
public class SourceVerificationAgentTool {

    private final VectorSearchTool vector;

    public SourceVerificationAgentTool(VectorSearchTool vector) {
        this.vector = vector;
    }

    @Tool("Search the document corpus for evidence supporting or contradicting a specific finding. " +
          "Returns raw matching passages — you assign the confidence level based on what is returned. " +
          "HIGH = multiple independent official sources. MEDIUM = one credible source. " +
          "LOW = single low-reliability source. UNVERIFIED = nothing found.")
    public String verifyClaim(
            @P("The claim text to find corroborating or contradicting documents for") String claim) {
        return vector.searchDocuments(claim);
    }
}
