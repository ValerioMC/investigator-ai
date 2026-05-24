package ai.investigator.agents.verification;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface SourceVerificationAgent {

    @SystemMessage(fromResource = "prompts/SourceVerificationAgent-system.txt")
    String verifyClaim(@UserMessage String claim);
}
