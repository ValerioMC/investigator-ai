package ai.investigator.graph.projection;

public record OwnershipEntry(
    String ownerName,
    double sharePercent,
    String ownershipType,
    String nationality
) {}
