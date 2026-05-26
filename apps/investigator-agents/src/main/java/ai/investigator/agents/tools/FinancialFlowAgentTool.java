package ai.investigator.agents.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class FinancialFlowAgentTool {

    private static final Logger log = LoggerFactory.getLogger(FinancialFlowAgentTool.class);

    private final GraphTraversalTool graph;

    public FinancialFlowAgentTool(GraphTraversalTool graph) {
        this.graph = graph;
    }

    @Tool("Retrieve public contract awards for a company from the graph database. " +
          "Returns contract titles, amounts, and awarding bodies found in the application database. " +
          "Balance sheet data is only available if financial records have been explicitly ingested. " +
          "Pass the exact legal company name.")
    public String analyzeFinancials(
            @P("Exact legal name of the company") String companyName) {
        log.warn("[FINANCIAL-TOOL] called for: {}", companyName);
        String result;
        try {
            result = graph.findContractsWonByCompany(companyName);
        } catch (Exception e) {
            log.error("[FINANCIAL-TOOL] findContractsWonByCompany threw {}: {}", e.getClass().getSimpleName(), e.getMessage(), e);
            result = "findContractsWonByCompany unavailable (" + e.getClass().getSimpleName() + ")";
        }
        log.warn("[FINANCIAL-TOOL] result for {}: {}", companyName, result);
        return result;
    }
}
