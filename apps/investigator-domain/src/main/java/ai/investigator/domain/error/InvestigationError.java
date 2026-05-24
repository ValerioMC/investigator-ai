package ai.investigator.domain.error;

public sealed interface InvestigationError
        permits EntityNotFound, InsufficientEvidence, SourceConflict, GraphTraversalError {}
