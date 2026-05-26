package ai.investigator.agents.person;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface PersonProfileAgent {

    @SystemMessage(fromResource = "prompts/PersonProfileAgent-system.txt")
    String analyze(@UserMessage String request);
}
