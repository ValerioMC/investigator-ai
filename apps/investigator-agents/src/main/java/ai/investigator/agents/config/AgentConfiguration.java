package ai.investigator.agents.config;

import ai.investigator.agents.corporate.CorporateAgent;
import ai.investigator.agents.document.DocumentAgent;
import ai.investigator.agents.financial.FinancialFlowAgent;
import ai.investigator.agents.observability.LangfuseObservabilityListener;
import ai.investigator.agents.observability.LangfuseProperties;
import ai.investigator.agents.person.PersonProfileAgent;
import ai.investigator.agents.supervisor.SupervisorAgent;
import ai.investigator.agents.verification.SourceVerificationAgent;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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

    // Shared model for every JSON-synthesis subagent and the Supervisor.
    // Ollama's native JSON format is enabled so each response is guaranteed-parseable.
    // numPredict is raised to 16k tokens so the Supervisor can emit a full merged
    // report (4096 default truncates the JSON mid-array on multi-finding outputs).
    @Bean("supervisorChatModel")
    public ChatModel supervisorChatModel(OllamaProperties props,
                                         Optional<LangfuseObservabilityListener> langfuse) {
        var builder = OllamaChatModel.builder()
            .baseUrl(props.getBaseUrl())
            .modelName(props.getModelId())
            .temperature(0.1)
            .think(false)
            .numCtx(props.getNumCtx())
            .numPredict(16384)
            .responseFormat(ResponseFormat.JSON)
            .timeout(Duration.ofSeconds(props.getTimeoutSeconds()));
        langfuse.ifPresent(l -> builder.listeners(List.of(l)));
        return builder.build();
    }

    // --- Subagents: no tools registered. They receive pre-collected data and
    //     emit JSON AgentReport. Each call is its own Langfuse trace. ---

    @Bean
    public CorporateAgent corporateAgent(@Qualifier("supervisorChatModel") ChatModel model) {
        return AiServices.builder(CorporateAgent.class).chatModel(model).build();
    }

    @Bean
    public PersonProfileAgent personProfileAgent(@Qualifier("supervisorChatModel") ChatModel model) {
        return AiServices.builder(PersonProfileAgent.class).chatModel(model).build();
    }

    @Bean
    public FinancialFlowAgent financialFlowAgent(@Qualifier("supervisorChatModel") ChatModel model) {
        return AiServices.builder(FinancialFlowAgent.class).chatModel(model).build();
    }

    @Bean
    public DocumentAgent documentAgent(@Qualifier("supervisorChatModel") ChatModel model) {
        return AiServices.builder(DocumentAgent.class).chatModel(model).build();
    }

    @Bean
    public SourceVerificationAgent sourceVerificationAgent(@Qualifier("supervisorChatModel") ChatModel model) {
        return AiServices.builder(SourceVerificationAgent.class).chatModel(model).build();
    }

    @Bean
    public SupervisorAgent supervisorAgent(@Qualifier("supervisorChatModel") ChatModel model) {
        return AiServices.builder(SupervisorAgent.class).chatModel(model).build();
    }
}
