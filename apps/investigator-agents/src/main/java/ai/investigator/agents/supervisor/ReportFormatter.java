package ai.investigator.agents.supervisor;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface ReportFormatter {

    @SystemMessage(fromResource = "prompts/ReportFormatter-system.txt")
    @UserMessage("""
            Original query: {{query}}

            Findings from investigation:
            {{findings}}
            """)
    String toJson(@V("query") String query, @V("findings") String findings);
}
