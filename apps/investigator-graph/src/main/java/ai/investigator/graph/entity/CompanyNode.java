package ai.investigator.graph.entity;

import org.neo4j.driver.types.Node;

public record CompanyNode(
    String id,
    String name,
    String registrationNumber,
    String jurisdiction,
    String legalForm,
    boolean active,
    String vatNumber,
    String sector
) {
    public static CompanyNode from(Node node) {
        return new CompanyNode(
            node.get("id").asString(),
            node.get("name").asString(),
            node.containsKey("registrationNumber") && !node.get("registrationNumber").isNull()
                ? node.get("registrationNumber").asString() : null,
            node.get("jurisdiction").asString(),
            node.containsKey("legalForm") && !node.get("legalForm").isNull()
                ? node.get("legalForm").asString() : "Unknown",
            node.containsKey("active") ? node.get("active").asBoolean(true) : true,
            node.containsKey("vatNumber") && !node.get("vatNumber").isNull()
                ? node.get("vatNumber").asString() : null,
            node.containsKey("sector") && !node.get("sector").isNull()
                ? node.get("sector").asString() : null
        );
    }
}
