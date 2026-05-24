package ai.investigator.graph.entity;

import org.neo4j.driver.types.Node;

import java.time.LocalDate;

/** Maps to :Person node in Neo4j. Separate from domain Person to keep graph deps out of domain. */
public record PersonNode(
    String id,
    String fullName,
    LocalDate birthDate,
    String nationality,
    String taxCode,
    String politicalRole,
    Double riskScore
) {
    public static PersonNode from(Node node) {
        return new PersonNode(
            node.get("id").asString(),
            node.get("fullName").asString(),
            node.containsKey("birthDate") && !node.get("birthDate").isNull()
                ? node.get("birthDate").asLocalDate() : null,
            node.containsKey("nationality") && !node.get("nationality").isNull()
                ? node.get("nationality").asString() : null,
            node.containsKey("taxCode") && !node.get("taxCode").isNull()
                ? node.get("taxCode").asString() : null,
            node.containsKey("politicalRole") && !node.get("politicalRole").isNull()
                ? node.get("politicalRole").asString() : null,
            node.containsKey("riskScore") && !node.get("riskScore").isNull()
                ? node.get("riskScore").asDouble() : null
        );
    }
}
