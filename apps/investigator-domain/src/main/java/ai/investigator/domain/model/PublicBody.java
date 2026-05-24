package ai.investigator.domain.model;

public record PublicBody(
        String id,
        String name,
        BodyLevel level,
        String country
) {

    public enum BodyLevel {
        NATIONAL,
        REGIONAL,
        MUNICIPAL,
        EU,
        OTHER
    }
}
