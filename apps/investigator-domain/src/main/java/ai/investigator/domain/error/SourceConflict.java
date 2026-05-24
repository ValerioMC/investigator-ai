package ai.investigator.domain.error;

public record SourceConflict(
        String claim,
        String source1,
        String source2
) implements InvestigationError {}
