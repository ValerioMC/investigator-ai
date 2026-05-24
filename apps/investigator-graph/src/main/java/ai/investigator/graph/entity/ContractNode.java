package ai.investigator.graph.entity;

import org.neo4j.driver.types.Node;

import java.time.LocalDate;

public record ContractNode(
    String id,
    String title,
    double amount,
    LocalDate awardedAt,
    String cpvCode,
    String publicBodyName,
    String source
) {
    public static ContractNode from(Node node) {
        return new ContractNode(
            node.get("id").asString(),
            node.get("title").asString(),
            node.get("amount").asDouble(0.0),
            node.containsKey("awardedAt") && !node.get("awardedAt").isNull()
                ? node.get("awardedAt").asLocalDate() : null,
            node.containsKey("cpvCode") && !node.get("cpvCode").isNull()
                ? node.get("cpvCode").asString() : null,
            node.get("publicBodyName").asString(),
            node.containsKey("source") && !node.get("source").isNull()
                ? node.get("source").asString() : "UNKNOWN"
        );
    }
}
