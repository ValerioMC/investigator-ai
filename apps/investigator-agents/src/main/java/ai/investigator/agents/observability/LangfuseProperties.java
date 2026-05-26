package ai.investigator.agents.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "langfuse")
public record LangfuseProperties(
        boolean enabled,
        String baseUrl,
        String publicKey,
        String secretKey
) {
    public LangfuseProperties {
        if (baseUrl == null) baseUrl = "http://localhost:3000";
        if (publicKey == null) publicKey = "";
        if (secretKey == null) secretKey = "";
    }
}
