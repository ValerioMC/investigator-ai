package ai.investigator.agents.tools;

import ai.investigator.agents.person.PersonProfileAgent;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

@Component
public class PersonProfileAgentTool {

    private final PersonProfileAgent personProfileAgent;

    public PersonProfileAgentTool(PersonProfileAgent personProfileAgent) {
        this.personProfileAgent = personProfileAgent;
    }

    @Tool("Build a comprehensive profile of an individual: public roles, directorships, " +
          "criminal record, family network, and potential conflicts of interest. " +
          "Use for any query about a specific person.")
    public String buildPersonProfile(
            @P("Investigative query about the person, e.g. 'Profile Luigi Conti, ex-mayor of Brescia'")
            String query) {
        return personProfileAgent.buildProfile(query);
    }
}
