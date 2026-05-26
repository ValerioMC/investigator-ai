package ai.investigator.graph.projection;

import java.time.LocalDate;

public record DocumentRef(
    String title,
    String sourceType,
    LocalDate publishedAt,
    String context,
    String reliability
) {}
