package ai.investigator.agents.tools;

import ai.investigator.agents.financial.FinancialFlowAgent;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

@Component
public class FinancialFlowAgentTool {

    private final FinancialFlowAgent financialFlowAgent;

    public FinancialFlowAgentTool(FinancialFlowAgent financialFlowAgent) {
        this.financialFlowAgent = financialFlowAgent;
    }

    @Tool("Analyze a company's financial data for anomalies: unusual profit margins, " +
          "suspicious dividend distributions, and revenue spikes correlated with public contract awards. " +
          "Use when financial forensics are needed.")
    public String analyzeFinancials(
            @P("Investigative query about the company's finances, e.g. 'Analyze Costruzioni Ferretti Srl financials 2022-2023'")
            String query) {
        return financialFlowAgent.analyzeFinancials(query);
    }
}
