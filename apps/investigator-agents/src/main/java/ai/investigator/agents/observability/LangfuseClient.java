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
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Thin HTTP client for Langfuse's ingestion endpoint. Centralises the auth +
 * batch envelope so both the LLM listener and the orchestrator can post events
 * (traces, spans, generations) without duplicating wiring.
 *
 * All posts run on virtual threads — they are fire-and-forget for observability
 * and must not block the investigation flow.
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

    /** Fire-and-forget post of one or more ingestion events. */
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

    // ----- Event builders -----

    public Map<String, Object> traceCreate(String traceId, String name,
                                           String sessionId, String userId,
                                           String input, List<String> tags) {
        var body = new LinkedHashMap<String, Object>();
        body.put("id", traceId);
        body.put("name", name);
        body.put("timestamp", Instant.now().toString());
        if (sessionId != null) body.put("sessionId", sessionId);
        if (userId != null) body.put("userId", userId);
        if (input != null) body.put("input", input);
        if (tags != null) body.put("tags", tags);
        return event("trace-create", body);
    }

    public Map<String, Object> traceUpdate(String traceId, String output) {
        var body = new LinkedHashMap<String, Object>();
        body.put("id", traceId);
        if (output != null) body.put("output", output);
        return event("trace-create", body); // Langfuse: upsert via the same event type
    }

    public Map<String, Object> spanCreate(String spanId, String traceId,
                                          String parentObservationId,
                                          String name, Instant startTime,
                                          String input, Map<String, Object> metadata) {
        var body = new LinkedHashMap<String, Object>();
        body.put("id", spanId);
        body.put("traceId", traceId);
        if (parentObservationId != null) body.put("parentObservationId", parentObservationId);
        body.put("name", name);
        body.put("startTime", startTime.toString());
        if (input != null) body.put("input", input);
        if (metadata != null) body.put("metadata", metadata);
        return event("span-create", body);
    }

    public Map<String, Object> spanUpdate(String spanId, String traceId,
                                          Instant endTime, String output, String level) {
        var body = new LinkedHashMap<String, Object>();
        body.put("id", spanId);
        body.put("traceId", traceId);
        body.put("endTime", endTime.toString());
        if (output != null) body.put("output", output);
        if (level != null) body.put("level", level);
        return event("span-update", body);
    }

    private Map<String, Object> event(String type, Map<String, Object> body) {
        var ev = new LinkedHashMap<String, Object>();
        ev.put("id", UUID.randomUUID().toString());
        ev.put("type", type);
        ev.put("timestamp", Instant.now().toString());
        ev.put("body", body);
        return ev;
    }
}
