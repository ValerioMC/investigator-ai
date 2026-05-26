package ai.investigator.graph.entity;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import java.time.LocalDate;

@Node("Contract")
public record ContractNode(
    @Id String id,
    String title,
    double amount,
    LocalDate awardedAt,
    String cpvCode,
    String publicBodyName,
    String source
) {}
