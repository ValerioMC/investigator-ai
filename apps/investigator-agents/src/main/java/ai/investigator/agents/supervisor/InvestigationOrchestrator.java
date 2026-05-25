package ai.investigator.agents.supervisor;

import ai.investigator.agents.tools.CorporateAgentTool;
import ai.investigator.agents.tools.DocumentAgentTool;
import ai.investigator.agents.tools.FinancialFlowAgentTool;
import ai.investigator.agents.tools.PersonProfileAgentTool;
import ai.investigator.agents.tools.SourceVerificationAgentTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Java orchestrator — no LLM involvement in data collection.
 * Calls each specialist tool sequentially for the given entities,
 * then hands the aggregated findings to SupervisorAgent for JSON synthesis.
 * This avoids relying on LLM tool-calling (which is brittle with local models).
 */
@Service
public class InvestigationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(InvestigationOrchestrator.class);

    private static final List<String> COMPANY_SUFFIXES =
        List.of("srl", "spa", "sa", "ltd", "gmbh", "inc", "bv", "nv", "llc", "plc", "corp", "offshore");

    private final CorporateAgentTool corporateTool;
    private final PersonProfileAgentTool personTool;
    private final FinancialFlowAgentTool financialTool;
    private final DocumentAgentTool documentTool;
    private final SourceVerificationAgentTool verificationTool;
    private final SupervisorAgent supervisor;

    public InvestigationOrchestrator(CorporateAgentTool corporateTool,
                                     PersonProfileAgentTool personTool,
                                     FinancialFlowAgentTool financialTool,
                                     DocumentAgentTool documentTool,
                                     SourceVerificationAgentTool verificationTool,
                                     SupervisorAgent supervisor) {
        this.corporateTool    = corporateTool;
        this.personTool       = personTool;
        this.financialTool    = financialTool;
        this.documentTool     = documentTool;
        this.verificationTool = verificationTool;
        this.supervisor       = supervisor;
    }

    public String investigate(String query, List<String> focusEntities) {
        log.info("Investigation start — query: {}, entities: {}", query, focusEntities);

        var findings = new StringBuilder();
        findings.append("=== INVESTIGATION FINDINGS ===\n\n");

        for (String entity : focusEntities) {
            if (isCompany(entity)) {
                log.info("Processing company: {}", entity);
                findings.append("--- CORPORATE ANALYSIS: ").append(entity).append(" ---\n");
                findings.append(corporateTool.analyzeCorporateOwnership(entity)).append("\n\n");
                findings.append("--- FINANCIAL ANALYSIS: ").append(entity).append(" ---\n");
                findings.append(financialTool.analyzeFinancials(entity)).append("\n\n");
            } else {
                log.info("Processing person: {}", entity);
                findings.append("--- PERSON PROFILE: ").append(entity).append(" ---\n");
                findings.append(personTool.buildPersonProfile(entity)).append("\n\n");
            }
        }

        findings.append("--- DOCUMENT SEARCH ---\n");
        findings.append(documentTool.searchDocuments(query)).append("\n\n");

        log.info("All tools executed. Passing to supervisor for JSON synthesis.");
        String prompt = "Query: " + query + "\n\n" + findings;
        return supervisor.investigate(prompt);
    }

    private boolean isCompany(String entity) {
        String lower = entity.toLowerCase();
        return COMPANY_SUFFIXES.stream().anyMatch(s -> lower.contains(" " + s) || lower.endsWith(s));
    }
}
