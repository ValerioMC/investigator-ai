package ai.investigator.domain.error;

public record EntityNotFound(
        String entityType,   // e.g. "Person", "Company"
        String identifier
) implements InvestigationError {}
