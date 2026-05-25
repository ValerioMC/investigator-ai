package ai.investigator.agents.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CorporateAgentTool {

    private static final Logger log = LoggerFactory.getLogger(CorporateAgentTool.class);

    private final GraphTraversalTool graph;

    public CorporateAgentTool(GraphTraversalTool graph) {
        this.graph = graph;
    }

    @Tool("Retrieve ownership chain, UBO, and public contracts for a company from the graph database. " +
          "Returns only data present in the application database — no inference. " +
          "Pass the exact legal company name as it appears in the registry.")
    public String analyzeCorporateOwnership(
            @P("Exact legal name of the company, e.g. 'Costruzioni Ferretti Srl'") String companyName) {
        log.warn("[CORPORATE-TOOL] called for: {}", companyName);
        String result = graph.findOwnershipChain(companyName) + "\n\n" +
               graph.findUBO(companyName) + "\n\n" +
               graph.findContractsWonByCompany(companyName);
        log.warn("[CORPORATE-TOOL] result for {}: {}", companyName, result);
        return result;
    }
}
