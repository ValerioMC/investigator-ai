package ai.investigator.domain.model;

/**
 * Normalised risk score in [0.0, 1.0].
 * 0.0 = no detected risk, 1.0 = maximum risk.
 */
public record RiskScore(float value) {

    public RiskScore {
        if (value < 0.0f || value > 1.0f) {
            throw new IllegalArgumentException("RiskScore must be in [0.0, 1.0], got: " + value);
        }
    }

    public static RiskScore of(float value) {
        return new RiskScore(value);
    }
}
