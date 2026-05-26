package ai.investigator.graph.projection;

public record TaxHavenEntry(
    String person,
    String company,
    String jurisdiction,
    String isoCode
) {}
