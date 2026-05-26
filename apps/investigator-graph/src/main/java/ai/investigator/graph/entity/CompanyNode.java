package ai.investigator.graph.entity;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("Company")
public record CompanyNode(
    @Id String id,
    String name,
    String registrationNumber,
    String jurisdiction,
    String legalForm,
    boolean active,
    String vatNumber,
    String sector
) {}
