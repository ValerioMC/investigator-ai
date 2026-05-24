package ai.investigator.domain.model;

import java.time.LocalDate;

public record Person(
        String id,
        String fullName,
        LocalDate birthDate,       // nullable
        String nationality,        // nullable
        String taxCode,            // nullable — Italian CF or equivalent
        String politicalRole,      // nullable — e.g. "Mayor of Rome 2019-2023"
        RiskScore riskScore        // nullable — computed by FinancialFlowAgent
) {}
