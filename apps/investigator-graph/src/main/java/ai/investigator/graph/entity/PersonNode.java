package ai.investigator.graph.entity;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

import java.time.LocalDate;

@Node("Person")
public record PersonNode(
    @Id String id,
    String fullName,
    LocalDate birthDate,
    String nationality,
    String taxCode,
    String politicalRole,
    @Property("riskScore") Double riskScore
) {}
