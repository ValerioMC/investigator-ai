package ai.investigator.domain.report;

import ai.investigator.domain.model.ConfidenceLevel;

import java.util.List;

public record Finding(
        String claim,
        ConfidenceLevel confidence,
        List<String> evidence,   // document IDs, graph paths, source URLs
        String agentSource       // e.g. "PersonProfileAgent"
) {}
