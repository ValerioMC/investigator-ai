package ai.investigator.api.resource;

import java.util.List;

public record InvestigateRequest(
    String query,
    int depth,
    List<String> focusEntities
) {
    public InvestigateRequest {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (depth < 1 || depth > 10) {
            depth = 3;
        }
        if (focusEntities == null) {
            focusEntities = List.of();
        }
    }
}
