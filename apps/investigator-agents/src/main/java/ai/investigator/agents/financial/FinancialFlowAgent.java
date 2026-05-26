package ai.investigator.agents.financial;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface FinancialFlowAgent {

    @SystemMessage(fromResource = "prompts/FinancialFlowAgent-system.txt")
    String analyze(@UserMessage String request);
}
