package ai.investigator.domain.report;

import java.util.List;

/**
 * Top-level output of the SupervisorAgent.
 * The disclaimer field is mandatory — never omit it before surfacing to users.
 */
public record InvestigationReport(
        String query,
        String summary,
        List<Finding> findings,
        EntityMap entityMap,
        List<String> recommendedFollowUps,
        String disclaimer
) {

    public InvestigationReport {
        if (disclaimer == null || disclaimer.isBlank()) {
            throw new IllegalArgumentException("disclaimer is required on every InvestigationReport");
        }
    }
}
