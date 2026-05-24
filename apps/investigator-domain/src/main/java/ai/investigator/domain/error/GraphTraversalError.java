package ai.investigator.domain.error;

public record GraphTraversalError(
        String query,  // the Cypher query that failed
        String cause
) implements InvestigationError {}
