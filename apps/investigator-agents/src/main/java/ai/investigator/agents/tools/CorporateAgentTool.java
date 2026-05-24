package ai.investigator.agents.tools;

import ai.investigator.agents.corporate.CorporateAgent;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

@Component
public class CorporateAgentTool {

    private final CorporateAgent corporateAgent;

    public CorporateAgentTool(CorporateAgent corporateAgent) {
        this.corporateAgent = corporateAgent;
    }

    @Tool("Trace the ownership chain of a company, identify the Ultimate Beneficial Owner (UBO), " +
          "detect shell companies, nominee directors, and tax haven registrations. " +
          "Use for any query about who controls or owns a company.")
    public String analyzeCorporateOwnership(
            @P("Investigative query about the company, e.g. 'Who controls Costruzioni Ferretti Srl?'")
            String query) {
        return corporateAgent.analyzeOwnership(query);
    }
}
