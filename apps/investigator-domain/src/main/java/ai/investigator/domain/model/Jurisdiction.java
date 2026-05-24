package ai.investigator.domain.model;

public record Jurisdiction(
        String id,
        String name,
        String isoCode,
        boolean taxHaven,
        boolean euMember
) {}
