package ai.investigator.agents.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

@Component
public class CorporateAgentTool {

    private final GraphTraversalTool graph;

    public CorporateAgentTool(GraphTraversalTool graph) {
        this.graph = graph;
    }

    @Tool("Retrieve ownership chain, UBO, and public contracts for a company from the graph database. " +
          "Returns only data present in the application database — no inference. " +
          "Pass the exact legal company name as it appears in the registry.")
    public String analyzeCorporateOwnership(
            @P("Exact legal name of the company, e.g. 'Costruzioni Ferretti Srl'") String companyName) {
        return graph.findOwnershipChain(companyName) + "\n\n" +
               graph.findUBO(companyName) + "\n\n" +
               graph.findContractsWonByCompany(companyName);
    }
}
