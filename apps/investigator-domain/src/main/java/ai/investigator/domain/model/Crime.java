package ai.investigator.domain.model;

public record Crime(
        String id,
        CrimeType type,
        String description,
        Severity severity,
        CrimeStatus status
) {

    public enum CrimeType {
        CORRUPTION,
        FRAUD,
        MONEY_LAUNDERING,
        TAX_EVASION,
        BRIBERY,
        EMBEZZLEMENT,
        OTHER
    }

    public enum Severity {
        FELONY,
        MISDEMEANOR,
        INVESTIGATION_ONLY
    }

    public enum CrimeStatus {
        CONVICTED,
        ACQUITTED,
        PENDING,
        ARCHIVED
    }
}
