package ai.investigator.agents.supervisor;

import ai.investigator.agents.corporate.CorporateAgent;
import ai.investigator.agents.document.DocumentAgent;
import ai.investigator.agents.financial.FinancialFlowAgent;
import ai.investigator.agents.person.PersonProfileAgent;
import ai.investigator.agents.verification.SourceVerificationAgent;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Subagents exposed as @Tool methods for the Supervisor LLM.
 * The Supervisor decides which subagent to call, in which order, and with which
 * arguments. Each call forwards to the subagent (itself an LLM) which retrieves
 * data through its own data tool and returns a JSON AgentReport.
 */
@Component
public class SubagentTools {

    private static final Logger log = LoggerFactory.getLogger(SubagentTools.class);

    private final CorporateAgent corporate;
    private final PersonProfileAgent person;
    private final FinancialFlowAgent financial;
    private final DocumentAgent document;
    private final SourceVerificationAgent verification;

    public SubagentTools(CorporateAgent corporate,
                         PersonProfileAgent person,
                         FinancialFlowAgent financial,
                         DocumentAgent document,
                         SourceVerificationAgent verification) {
        this.corporate    = corporate;
        this.person       = person;
        this.financial    = financial;
        this.document     = document;
        this.verification = verification;
    }

    @Tool("Run the CorporateAgent on a company. Returns a JSON AgentReport describing ownership chain, " +
          "UBO, tax-haven exposure and public contracts won. Pass the exact legal name.")
    public String runCorporateAgent(@P("Exact legal company name") String companyName) {
        log.info("[SUPERVISOR-CALL] CorporateAgent({})", companyName);
        return invokeSafely("CorporateAgent",
            () -> corporate.analyze("Investigate corporate ownership of: " + companyName));
    }

    @Tool("Run the PersonProfileAgent on a person. Returns a JSON AgentReport describing companies they " +
          "own or direct, public roles, family network, conflicts of interest, convictions, tax-haven links, " +
          "and document mentions. Pass the full legal name.")
    public String runPersonProfileAgent(@P("Exact full name of the person") String personName) {
        log.info("[SUPERVISOR-CALL] PersonProfileAgent({})", personName);
        return invokeSafely("PersonProfileAgent",
            () -> person.analyze("Profile this person: " + personName));
    }

    @Tool("Run the FinancialFlowAgent on a company. Returns a JSON AgentReport on public contracts " +
          "awarded, total amounts, and concentration risk. Use exact legal company name.")
    public String runFinancialFlowAgent(@P("Exact legal company name") String companyName) {
        log.info("[SUPERVISOR-CALL] FinancialFlowAgent({})", companyName);
        return invokeSafely("FinancialFlowAgent",
            () -> financial.analyze("Analyze financial flows for: " + companyName));
    }

    @Tool("Run the DocumentAgent. Returns a JSON AgentReport summarising the most relevant document " +
          "hits (news, court records, filings) for a free-text query — mention entity names verbatim.")
    public String runDocumentAgent(@P("Free-text search query referencing entities of interest") String query) {
        log.info("[SUPERVISOR-CALL] DocumentAgent({})", query);
        return invokeSafely("DocumentAgent",
            () -> document.analyze("Find documentary evidence about: " + query));
    }

    @Tool("Run the SourceVerificationAgent on a specific claim. Returns a JSON AgentReport indicating " +
          "whether the claim is corroborated by documents and/or deterministic graph cross-links, " +
          "with a confidence rating.")
    public String runSourceVerificationAgent(@P("The specific claim to verify, phrased as a single sentence") String claim) {
        log.info("[SUPERVISOR-CALL] SourceVerificationAgent({})", claim);
        return invokeSafely("SourceVerificationAgent",
            () -> verification.analyze("Verify this claim: " + claim));
    }

    // The Supervisor wraps each subagent call into a ToolExecutionResultMessage, which
    // LangChain4j refuses to construct from null or empty text (throws
    // IllegalArgumentException("Either text or contents must be provided")). So we always
    // return a non-empty payload, even on subagent failure — turning a hard crash into a
    // graceful degraded finding.
    private String invokeSafely(String agentName, Supplier<String> call) {
        try {
            String result = call.get();
            if (result == null || result.isBlank()) {
                log.warn("[SUPERVISOR-CALL] {} returned empty — returning empty AgentReport", agentName);
                return emptyReport(agentName, "subagent returned no content");
            }
            return result;
        } catch (Exception e) {
            log.error("[SUPERVISOR-CALL] {} failed: {}", agentName, e.toString(), e);
            return emptyReport(agentName, "subagent call failed: " + e.getMessage());
        }
    }

    private String emptyReport(String agentName, String reason) {
        return "{\"agent_source\":\"" + agentName + "\"," +
               "\"findings\":[{\"claim\":\"" + reason.replace("\"", "'") +
               "\",\"confidence\":\"UNVERIFIED\",\"evidence\":[]}]," +
               "\"entities\":{\"persons\":[],\"companies\":[],\"contracts\":[]}}";
    }
}
