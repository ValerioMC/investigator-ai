package ai.investigator.domain.error;

public class InvestigationException extends RuntimeException {

    private final InvestigationError error;

    public InvestigationException(InvestigationError error) {
        super(error.toString());
        this.error = error;
    }

    public InvestigationError getError() {
        return error;
    }
}
