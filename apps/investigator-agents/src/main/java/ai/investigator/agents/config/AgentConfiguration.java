package ai.investigator.agents.config;

import ai.investigator.agents.corporate.CorporateAgent;
import ai.investigator.agents.document.DocumentAgent;
import ai.investigator.agents.financial.FinancialFlowAgent;
import ai.investigator.agents.person.PersonProfileAgent;
import ai.investigator.agents.supervisor.ReportFormatter;
import ai.investigator.agents.supervisor.SupervisorAgent;
import ai.investigator.agents.tools.CorporateAgentTool;
import ai.investigator.agents.tools.DocumentAgentTool;
import ai.investigator.agents.tools.FinancialAnalysisTool;
import ai.investigator.agents.tools.FinancialFlowAgentTool;
import ai.investigator.agents.tools.GraphTraversalTool;
import ai.investigator.agents.tools.GuardrailTool;
import ai.investigator.agents.tools.PersonProfileAgentTool;
import ai.investigator.agents.tools.SourceVerificationAgentTool;
import ai.investigator.agents.tools.VectorSearchTool;
import ai.investigator.agents.observability.LangfuseObservabilityListener;
import ai.investigator.agents.observability.LangfuseProperties;
import ai.investigator.agents.verification.SourceVerificationAgent;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Configuration
@EnableConfigurationProperties({OllamaProperties.class, LangfuseProperties.class})
public class AgentConfiguration {

    @Bean
    @Primary
    public ChatModel ollamaChatModel(OllamaProperties props,
                                     Optional<LangfuseObservabilityListener> langfuse) {
        var builder = OllamaChatModel.builder()
            .baseUrl(props.getBaseUrl())
            .modelName(props.getModelId())
            .temperature(props.getTemperature())
            .think(false)
            .numCtx(props.getNumCtx())
            .numPredict(props.getNumPredict())
            .timeout(Duration.ofSeconds(props.getTimeoutSeconds()));
        langfuse.ifPresent(l -> builder.listeners(List.of(l)));
        return builder.build();
    }

    // Separate bean for the supervisor: same settings but used to distinguish
    // supervisor traces from sub-agent traces in Langfuse.
    // responseFormat=JSON intentionally absent — Ollama cannot do tool calls
    // and JSON mode simultaneously (tool calls are handled by the delegate tools).
    @Bean
    public ChatModel supervisorChatModel(OllamaProperties props,
                                         Optional<LangfuseObservabilityListener> langfuse) {
        var builder = OllamaChatModel.builder()
            .baseUrl(props.getBaseUrl())
            .modelName(props.getModelId())
            .temperature(props.getTemperature())
            .think(false)
            .numCtx(props.getNumCtx())
            .numPredict(props.getNumPredict())
            .timeout(Duration.ofSeconds(props.getTimeoutSeconds()));
        langfuse.ifPresent(l -> builder.listeners(List.of(l)));
        return builder.build();
    }

    // Supervisor: calls delegate tools that each invoke a sub-agent LLM.
    // System prompt deliberately has no JSON template — the model is more likely
    // to call tools when it has no pre-formed output structure to fill in directly.
    // JSON formatting is handled separately by ReportFormatter after this call returns.
    @Bean
    public SupervisorAgent supervisorAgent(@Qualifier("supervisorChatModel") ChatModel supervisorChatModel,
                                           CorporateAgentTool corporateTool,
                                           PersonProfileAgentTool personTool,
                                           FinancialFlowAgentTool financialTool,
                                           DocumentAgentTool documentTool,
                                           SourceVerificationAgentTool verificationTool) {
        return AiServices.builder(SupervisorAgent.class)
            .chatModel(supervisorChatModel)
            .tools(corporateTool, personTool, financialTool, documentTool, verificationTool)
            .build();
    }

    // Formatter: no tools, pure JSON synthesis from supervisor's plain-text output.
    @Bean
    public ReportFormatter reportFormatter(@Qualifier("supervisorChatModel") ChatModel supervisorChatModel) {
        return AiServices.builder(ReportFormatter.class)
            .chatModel(supervisorChatModel)
            .build();
    }

    @Bean
    public CorporateAgent corporateAgent(ChatModel model, GraphTraversalTool graphTool) {
        return AiServices.builder(CorporateAgent.class)
            .chatModel(model)
            .tools(graphTool)
            .build();
    }

    @Bean
    public PersonProfileAgent personProfileAgent(ChatModel model,
                                                  GraphTraversalTool graphTool,
                                                  VectorSearchTool vectorTool) {
        return AiServices.builder(PersonProfileAgent.class)
            .chatModel(model)
            .tools(graphTool, vectorTool)
            .build();
    }

    @Bean
    public FinancialFlowAgent financialFlowAgent(ChatModel model,
                                                  FinancialAnalysisTool financialTool,
                                                  GraphTraversalTool graphTool) {
        return AiServices.builder(FinancialFlowAgent.class)
            .chatModel(model)
            .tools(financialTool, graphTool)
            .build();
    }

    @Bean
    public DocumentAgent documentAgent(ChatModel model, VectorSearchTool vectorTool) {
        return AiServices.builder(DocumentAgent.class)
            .chatModel(model)
            .tools(vectorTool)
            .build();
    }

    @Bean
    public SourceVerificationAgent sourceVerificationAgent(ChatModel model,
                                                            GraphTraversalTool graphTool,
                                                            VectorSearchTool vectorTool) {
        return AiServices.builder(SourceVerificationAgent.class)
            .chatModel(model)
            .tools(graphTool, vectorTool)
            .build();
    }
}
