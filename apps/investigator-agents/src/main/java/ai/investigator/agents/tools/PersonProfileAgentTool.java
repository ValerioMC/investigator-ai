package ai.investigator.agents.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

@Component
public class PersonProfileAgentTool {

    private final GraphTraversalTool graph;
    private final VectorSearchTool vector;

    public PersonProfileAgentTool(GraphTraversalTool graph, VectorSearchTool vector) {
        this.graph = graph;
        this.vector = vector;
    }

    @Tool("Retrieve a person's companies, criminal record, family network, conflicts of interest, " +
          "tax haven connections, and document mentions from the application databases. " +
          "Returns only data that exists in the graph and vector stores — no inference. " +
          "Pass the exact full name of the person.")
    public String buildPersonProfile(
            @P("Exact full name of the person, e.g. 'Luigi Conti'") String personName) {
        return graph.findCompaniesByPerson(personName) + "\n\n" +
               graph.findConvictions(personName) + "\n\n" +
               graph.findFamilyNetwork(personName) + "\n\n" +
               graph.detectConflictOfInterest(personName, null, null) + "\n\n" +
               graph.findTaxHavenConnections(personName) + "\n\n" +
               vector.searchDocumentsForPerson(personName);
    }
}
