package ai.investigator.agents.config;

import ai.investigator.agents.corporate.CorporateAgent;
import ai.investigator.agents.document.DocumentAgent;
import ai.investigator.agents.financial.FinancialFlowAgent;
import ai.investigator.agents.person.PersonProfileAgent;
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
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ResponseFormat;
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

    // Supervisor needs valid JSON output every time — force JSON response format.
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
            .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
            .responseFormat(ResponseFormat.JSON);
        langfuse.ifPresent(l -> builder.listeners(List.of(l)));
        return builder.build();
    }

    @Bean
    public SupervisorAgent supervisorAgent(@Qualifier("supervisorChatModel") ChatModel supervisorChatModel,
                                           CorporateAgentTool corporateTool,
                                           PersonProfileAgentTool personTool,
                                           FinancialFlowAgentTool financialTool,
                                           DocumentAgentTool documentTool,
                                           SourceVerificationAgentTool verificationTool,
                                           GuardrailTool guardrailTool) {
        return AiServices.builder(SupervisorAgent.class)
            .chatModel(supervisorChatModel)
            .tools(corporateTool, personTool, financialTool, documentTool, verificationTool, guardrailTool)
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
