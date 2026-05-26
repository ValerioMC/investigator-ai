package ai.investigator.agents.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class PersonProfileAgentTool {

    private static final Logger log = LoggerFactory.getLogger(PersonProfileAgentTool.class);

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
            @P("Exact full name of the person") String personName) {
        log.warn("[PERSON-TOOL] called for: {}", personName);
        StringBuilder sb = new StringBuilder();
        sb.append(safe("findCompaniesByPerson",    () -> graph.findCompaniesByPerson(personName))).append("\n\n");
        sb.append(safe("findConvictions",          () -> graph.findConvictions(personName))).append("\n\n");
        sb.append(safe("findFamilyNetwork",        () -> graph.findFamilyNetwork(personName))).append("\n\n");
        sb.append(safe("detectConflictOfInterest", () -> graph.detectConflictOfInterest(personName, null, null))).append("\n\n");
        sb.append(safe("findTaxHavenConnections",  () -> graph.findTaxHavenConnections(personName))).append("\n\n");
        sb.append(safe("searchDocumentsForPerson", () -> vector.searchDocumentsForPerson(personName)));
        String result = sb.toString();
        log.warn("[PERSON-TOOL] result for {}: {}", personName, result);
        return result;
    }

    private String safe(String op, Supplier<String> call) {
        try {
            return call.get();
        } catch (Exception e) {
            log.error("[PERSON-TOOL] {} threw {}: {}", op, e.getClass().getSimpleName(), e.getMessage(), e);
            return op + " unavailable (" + e.getClass().getSimpleName() + ")";
        }
    }
}
