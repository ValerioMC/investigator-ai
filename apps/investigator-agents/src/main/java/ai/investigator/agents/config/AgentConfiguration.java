package ai.investigator.agents.config;

import ai.investigator.agents.corporate.CorporateAgent;
import ai.investigator.agents.document.DocumentAgent;
import ai.investigator.agents.financial.FinancialFlowAgent;
import ai.investigator.agents.observability.LangfuseObservabilityListener;
import ai.investigator.agents.observability.LangfuseProperties;
import ai.investigator.agents.person.PersonProfileAgent;
import ai.investigator.agents.supervisor.SubagentTools;
import ai.investigator.agents.supervisor.SupervisorAgent;
import ai.investigator.agents.tools.CorporateAgentTool;
import ai.investigator.agents.tools.DocumentAgentTool;
import ai.investigator.agents.tools.FinancialFlowAgentTool;
import ai.investigator.agents.tools.PersonProfileAgentTool;
import ai.investigator.agents.tools.SourceVerificationAgentTool;
import ai.investigator.agents.verification.SourceVerificationAgent;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Configuration
@EnableConfigurationProperties({MlxProperties.class, LangfuseProperties.class})
public class AgentConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AgentConfiguration.class);

    @Bean
    public ChatModel chatModel(MlxProperties props,
                               Optional<LangfuseObservabilityListener> langfuse) {
        log.info("LLM provider: MLX [{} @ {}]", props.getModelId(), props.getBaseUrl());
        var builder = OpenAiChatModel.builder()
            .baseUrl(props.getBaseUrl())
            .apiKey("not-needed")
            .modelName(props.getModelId())
            .temperature(props.getTemperature())
            .maxTokens(props.getMaxTokens())
            .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
            .responseFormat("json_object")
            .strictTools(false);
        langfuse.ifPresent(l -> builder.listeners(List.of(l)));
        return builder.build();
    }

    @Bean
    public CorporateAgent corporateAgent(ChatModel model, CorporateAgentTool tool) {
        return AiServices.builder(CorporateAgent.class)
            .chatModel(model)
            .tools(tool)
            .build();
    }

    @Bean
    public PersonProfileAgent personProfileAgent(ChatModel model, PersonProfileAgentTool tool) {
        return AiServices.builder(PersonProfileAgent.class)
            .chatModel(model)
            .tools(tool)
            .build();
    }

    @Bean
    public FinancialFlowAgent financialFlowAgent(ChatModel model, FinancialFlowAgentTool tool) {
        return AiServices.builder(FinancialFlowAgent.class)
            .chatModel(model)
            .tools(tool)
            .build();
    }

    @Bean
    public DocumentAgent documentAgent(ChatModel model, DocumentAgentTool tool) {
        return AiServices.builder(DocumentAgent.class)
            .chatModel(model)
            .tools(tool)
            .build();
    }

    @Bean
    public SourceVerificationAgent sourceVerificationAgent(ChatModel model, SourceVerificationAgentTool tool) {
        return AiServices.builder(SourceVerificationAgent.class)
            .chatModel(model)
            .tools(tool)
            .build();
    }

    @Bean
    public SupervisorAgent supervisorAgent(ChatModel model, SubagentTools subagents) {
        return AiServices.builder(SupervisorAgent.class)
            .chatModel(model)
            .tools(subagents)
            .build();
    }
}
