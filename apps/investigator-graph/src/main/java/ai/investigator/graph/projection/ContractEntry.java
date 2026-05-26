package ai.investigator.graph.projection;

import java.time.LocalDate;

public record ContractEntry(
    String title,
    double amount,
    LocalDate awardedAt,
    String issuedBy
) {}
