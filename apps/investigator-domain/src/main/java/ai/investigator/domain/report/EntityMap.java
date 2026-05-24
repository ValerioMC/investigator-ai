package ai.investigator.domain.report;

import java.util.List;

/**
 * Entity IDs referenced in the report, grouped by type.
 * Consumers can use these to fetch full records from the graph or vector store.
 */
public record EntityMap(
        List<String> persons,
        List<String> companies,
        List<String> contracts
) {

    public static EntityMap empty() {
        return new EntityMap(List.of(), List.of(), List.of());
    }
}
