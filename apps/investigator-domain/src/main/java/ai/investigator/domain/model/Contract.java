package ai.investigator.domain.model;

import java.time.LocalDate;

public record Contract(
        String id,
        String title,
        double amount,
        LocalDate awardedAt,
        String cpvCode,        // nullable — EU Common Procurement Vocabulary code
        String publicBodyName,
        String source,
        String sourceUrl       // nullable
) {}
