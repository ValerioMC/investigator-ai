package ai.investigator.domain.model;

import java.time.LocalDate;

public record Transaction(
        String id,
        double amount,
        String currency,
        LocalDate date,
        String description,  // nullable
        boolean suspicious   // flagged by FinancialFlowAgent heuristics
) {}
