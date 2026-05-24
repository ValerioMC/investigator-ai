package ai.investigator.domain.error;

public record InsufficientEvidence(
        String claim,
        String reason
) implements InvestigationError {}
