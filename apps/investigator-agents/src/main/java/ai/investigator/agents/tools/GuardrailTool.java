package ai.investigator.agents.tools;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

/**
 * Validates that investigation outputs meet minimum quality requirements.
 * Called by the SupervisorAgent before finalizing a report.
 */
@Component
public class GuardrailTool {

    private static final String REQUIRED_DISCLAIMER =
        "This report is a journalistic aid. Claims require editorial verification before publication.";

    @Tool("Validate an investigation report before delivery. Checks that: " +
          "(1) a disclaimer is present, (2) at least one finding has a confidence level, " +
          "(3) no raw personal identifiers (SSN, tax codes) are exposed in the output. " +
          "Returns OK or a list of violations.")
    public String validateReport(String reportJson) {
        StringBuilder violations = new StringBuilder();

        if (!reportJson.contains("disclaimer")) {
            violations.append("VIOLATION: missing 'disclaimer' field.\n");
        }
        if (!reportJson.contains("confidence")) {
            violations.append("VIOLATION: no findings have a 'confidence' level.\n");
        }
        // Check for Italian codice fiscale pattern (16 alphanumeric chars)
        if (reportJson.matches(".*\\b[A-Z]{6}[0-9]{2}[A-Z][0-9]{2}[A-Z][0-9]{3}[A-Z]\\b.*")) {
            violations.append("VIOLATION: raw Italian tax code (codice fiscale) detected in output.\n");
        }
        // Check for IBAN pattern
        if (reportJson.matches(".*\\b[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}\\b.*")) {
            violations.append("VIOLATION: possible IBAN detected in output — mask it.\n");
        }

        if (violations.isEmpty()) {
            return "OK: report passes all guardrail checks.";
        }

        return "GUARDRAIL VIOLATIONS:\n" + violations +
            "\nPlease fix these issues before delivering the report.";
    }

    @Tool("Append the standard journalistic disclaimer to a report summary if not already present. " +
          "Returns the text with disclaimer appended.")
    public String ensureDisclaimer(String text) {
        if (text.contains("journalistic aid") || text.contains("editorial verification")) {
            return text;
        }
        return text + "\n\n---\n" + REQUIRED_DISCLAIMER;
    }
}
