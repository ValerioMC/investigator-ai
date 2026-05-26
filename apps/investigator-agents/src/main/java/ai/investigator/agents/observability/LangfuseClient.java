package ai.investigator.agents.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Thin HTTP client for Langfuse's ingestion endpoint. Fire-and-forget POSTs
 * run on virtual threads so observability never blocks the investigation.
 */
@Component
@ConditionalOnProperty(prefix = "langfuse", name = "enabled", havingValue = "true")
public class LangfuseClient {

    private static final Logger log = LoggerFactory.getLogger(LangfuseClient.class);

    private final String ingestionUrl;
    private final String authHeader;
    private final HttpClient http;
    private final ObjectMapper mapper;

    public LangfuseClient(LangfuseProperties props) {
        this.ingestionUrl = props.baseUrl() + "/api/public/ingestion";
        var creds = props.publicKey() + ":" + props.secretKey();
        this.authHeader = "Basic " + Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8));
        this.http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        this.mapper = new ObjectMapper();
    }

    public void postEvents(List<Map<String, Object>> events) {
        Thread.ofVirtual().start(() -> {
            try {
                var body = mapper.writeValueAsBytes(Map.of("batch", events));
                var req = HttpRequest.newBuilder()
                    .uri(URI.create(ingestionUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", authHeader)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
                var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 300) {
                    log.warn("Langfuse returned {}: {}", resp.statusCode(), resp.body());
                }
            } catch (Exception e) {
                log.warn("Langfuse ingestion failed [{}]: {}",
                    e.getClass().getSimpleName(), e.getMessage());
            }
        });
    }
}
