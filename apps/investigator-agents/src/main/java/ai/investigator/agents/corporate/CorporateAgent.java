package ai.investigator.agents.corporate;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface CorporateAgent {

    @SystemMessage(fromResource = "prompts/CorporateAgent-system.txt")
    String analyze(@UserMessage String request);
}
