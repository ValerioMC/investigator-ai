package ai.investigator.agents.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

@Component
public class FinancialFlowAgentTool {

    private final GraphTraversalTool graph;

    public FinancialFlowAgentTool(GraphTraversalTool graph) {
        this.graph = graph;
    }

    @Tool("Retrieve public contract awards for a company from the graph database. " +
          "Returns contract titles, amounts, and awarding bodies found in the application database. " +
          "Balance sheet data is only available if financial records have been explicitly ingested. " +
          "Pass the exact legal company name.")
    public String analyzeFinancials(
            @P("Exact legal name of the company, e.g. 'Costruzioni Ferretti Srl'") String companyName) {
        return graph.findContractsWonByCompany(companyName);
    }
}
