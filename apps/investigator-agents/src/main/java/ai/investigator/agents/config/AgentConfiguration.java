package ai.investigator.agents.config;

import ai.investigator.agents.supervisor.SupervisorAgent;
import ai.investigator.agents.observability.LangfuseObservabilityListener;
import ai.investigator.agents.observability.LangfuseProperties;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
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

    // Supervisor: no tools registered — its only job is JSON synthesis from provided findings.
    // Data collection is handled by InvestigationOrchestrator in Java (no LLM tool-calling).
    @Bean
    public SupervisorAgent supervisorAgent(ChatModel model) {
        return AiServices.builder(SupervisorAgent.class)
            .chatModel(model)
            .build();
    }
}
