package ai.investigator.agents.document;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface DocumentAgent {

    @SystemMessage(fromResource = "prompts/DocumentAgent-system.txt")
    String searchAndSynthesize(@UserMessage String query);
}
