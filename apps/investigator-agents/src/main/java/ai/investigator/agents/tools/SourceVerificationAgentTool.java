package ai.investigator.agents.tools;

import ai.investigator.agents.verification.SourceVerificationAgent;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

@Component
public class SourceVerificationAgentTool {

    private final SourceVerificationAgent sourceVerificationAgent;

    public SourceVerificationAgentTool(SourceVerificationAgent sourceVerificationAgent) {
        this.sourceVerificationAgent = sourceVerificationAgent;
    }

    @Tool("Cross-reference a specific claim against all available sources (graph + documents) " +
          "and assign a confidence level: HIGH, MEDIUM, LOW, or UNVERIFIED. " +
          "Call this for each key finding before including it in the final report.")
    public String verifyClaim(
            @P("The specific claim to verify, e.g. 'Luigi Conti voted on contracts awarding €1.2M to a company his brother owns'")
            String claim) {
        return sourceVerificationAgent.verifyClaim(claim);
    }
}
