package ai.investigator.graph.projection;

import java.time.LocalDate;

public record ConflictEntry(
    String person,
    String publicBody,
    String contract,
    double amount,
    LocalDate awardedAt,
    String company
) {}
