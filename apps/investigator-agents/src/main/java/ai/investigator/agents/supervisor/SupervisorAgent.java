package ai.investigator.agents.supervisor;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface SupervisorAgent {

    @SystemMessage(fromResource = "prompts/SupervisorAgent-system.txt")
    String investigate(@UserMessage String query);
}
