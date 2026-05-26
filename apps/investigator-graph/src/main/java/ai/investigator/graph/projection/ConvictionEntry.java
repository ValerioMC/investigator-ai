package ai.investigator.graph.projection;

public record ConvictionEntry(
    String type,
    String description,
    String severity,
    String status,
    int year,
    String court
) {}
