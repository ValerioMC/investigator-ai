package ai.investigator.agents.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

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
            @P("Exact legal name of the company") String companyName) {
        log.warn("[CORPORATE-TOOL] called for: {}", companyName);
        StringBuilder sb = new StringBuilder();
        sb.append(safe("findOwnershipChain", () -> graph.findOwnershipChain(companyName))).append("\n\n");
        sb.append(safe("findUBO",            () -> graph.findUBO(companyName))).append("\n\n");
        sb.append(safe("findContractsWonByCompany", () -> graph.findContractsWonByCompany(companyName)));
        String result = sb.toString();
        log.warn("[CORPORATE-TOOL] result for {}: {}", companyName, result);
        return result;
    }

    private String safe(String op, Supplier<String> call) {
        try {
            return call.get();
        } catch (Exception e) {
            log.error("[CORPORATE-TOOL] {} threw {}: {}", op, e.getClass().getSimpleName(), e.getMessage(), e);
            return op + " unavailable (" + e.getClass().getSimpleName() + ")";
        }
    }
}
