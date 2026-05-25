package ai.investigator.agents.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
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

@Component
@ConditionalOnProperty(prefix = "langfuse", name = "enabled", havingValue = "true")
public class LangfuseObservabilityListener implements ChatModelListener {

    private static final Logger log = LoggerFactory.getLogger(LangfuseObservabilityListener.class);

    private static final String TRACE_ID   = "lf.traceId";
    private static final String GEN_ID     = "lf.genId";
    private static final String START_KEY  = "lf.startTime";
    private static final String SESSION_ID = "lf.sessionId";
    private static final String USER_ID    = "lf.userId";

    private final String ingestionUrl;
    private final String authHeader;
    private final HttpClient http;
    private final ObjectMapper mapper;

    public LangfuseObservabilityListener(LangfuseProperties props) {
        this.ingestionUrl = props.baseUrl() + "/api/public/ingestion";
        var creds = props.publicKey() + ":" + props.secretKey();
        this.authHeader = "Basic " + Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8));
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.mapper = new ObjectMapper();
        log.info("Langfuse tracing enabled → {}", ingestionUrl);
    }

    @Override
    public void onRequest(ChatModelRequestContext ctx) {
        ctx.attributes().put(TRACE_ID,  UUID.randomUUID().toString());
        ctx.attributes().put(GEN_ID,    UUID.randomUUID().toString());
        ctx.attributes().put(START_KEY, Instant.now());
        // Capture the session/user from the per-thread context at request time,
        // since onResponse runs on a virtual thread that may not inherit it.
        var sid = LangfuseTraceContext.session();
        if (sid != null) ctx.attributes().put(SESSION_ID, sid);
        var uid = LangfuseTraceContext.user();
        if (uid != null) ctx.attributes().put(USER_ID, uid);
    }

    @Override
    public void onResponse(ChatModelResponseContext ctx) {
        var traceId   = (String)  ctx.attributes().get(TRACE_ID);
        var genId     = (String)  ctx.attributes().get(GEN_ID);
        var startTime = (Instant) ctx.attributes().get(START_KEY);
        if (traceId == null) return;

        var req     = ctx.chatRequest();
        var resp    = ctx.chatResponse();
        var endTime = Instant.now();

        Thread.ofVirtual().start(() -> {
            try {
                var agentName = resolveAgentName(req.messages());
                var userInput = extractUserInput(req.messages());
                var output    = resp.aiMessage() != null ? resp.aiMessage().text() : "";
                var usage     = resp.tokenUsage();
                var model     = resp.modelName() != null ? resp.modelName()
                                : req.modelName() != null ? req.modelName() : "ollama";

                var sessionId = (String) ctx.attributes().get(SESSION_ID);
                var userId    = (String) ctx.attributes().get(USER_ID);

                var traceBody = new LinkedHashMap<String, Object>();
                traceBody.put("id", traceId);
                traceBody.put("name", agentName);
                traceBody.put("timestamp", startTime.toString());
                traceBody.put("input", userInput);
                traceBody.put("output", output);
                traceBody.put("tags", List.of("investigator-ai", agentName));
                if (sessionId != null) traceBody.put("sessionId", sessionId);
                if (userId != null) traceBody.put("userId", userId);

                var genBody = new LinkedHashMap<String, Object>();
                genBody.put("id", genId);
                genBody.put("traceId", traceId);
                genBody.put("name", agentName);
                genBody.put("startTime", startTime.toString());
                genBody.put("endTime", endTime.toString());
                genBody.put("model", model);
                genBody.put("input", messagesAsJson(req.messages()));
                genBody.put("output", Map.of("role", "assistant", "content", output));
                if (usage != null) {
                    genBody.put("usage", map(
                            "input",  usage.inputTokenCount(),
                            "output", usage.outputTokenCount(),
                            "total",  usage.totalTokenCount()
                    ));
                }

                var batch = Map.of("batch", List.of(
                        event("trace-create",      traceId, startTime, traceBody),
                        event("generation-create", genId,   startTime, genBody)
                ));

                post(batch);
            } catch (Exception e) {
                log.warn("Langfuse ingestion failed [{}]: {}", e.getClass().getSimpleName(), e.getMessage(), e);
            }
        });
    }

    @Override
    public void onError(ChatModelErrorContext ctx) {
        var traceId = (String) ctx.attributes().get(TRACE_ID);
        if (traceId == null) return;
        Thread.ofVirtual().start(() -> {
            try {
                post(Map.of("batch", List.of(event("trace-create", traceId, Instant.now(),
                        map("id", traceId, "name", "investigator-ai",
                            "level", "ERROR",
                            "statusMessage", ctx.error().getMessage())))));  // NOSONAR: fire-and-forget
            } catch (Exception e) {
                log.warn("Langfuse error trace failed: {}", e.getMessage());
            }
        });
    }

    // --- helpers ---

    private String resolveAgentName(List<ChatMessage> messages) {
        return SystemMessage.findFirst(messages)
                .map(sm -> {
                    var text = sm.text();
                    if (text.contains("corporate intelligence"))          return "CorporateAgent";
                    if (text.contains("person intelligence"))             return "PersonProfileAgent";
                    if (text.contains("financial forensics"))             return "FinancialFlowAgent";
                    if (text.contains("document intelligence"))           return "DocumentAgent";
                    if (text.contains("source verification"))             return "SourceVerificationAgent";
                    if (text.contains("investigative journalism report writer") ||
                        text.contains("investigative journalism supervisor")) return "SupervisorAgent";
                    return "UnknownAgent";
                })
                .orElse("LLMCall");
    }

    private String extractUserInput(List<ChatMessage> messages) {
        return UserMessage.findLast(messages)
                .map(UserMessage::singleText)
                .orElse("");
    }

    private List<Map<String, String>> messagesAsJson(List<ChatMessage> messages) {
        return messages.stream()
                .map(m -> Map.of("role", roleOf(m), "content", textOf(m)))
                .toList();
    }

    private String textOf(ChatMessage m) {
        return switch (m) {
            case SystemMessage sm               -> sm.text();
            case UserMessage um                 -> um.hasSingleText() ? um.singleText() : um.toString();
            case AiMessage am                   -> am.text() != null ? am.text() : "";
            case ToolExecutionResultMessage tr  -> tr.text() != null ? tr.text() : "";
            default                             -> "";
        };
    }

    private String roleOf(ChatMessage m) {
        return switch (m.type()) {
            case SYSTEM                -> "system";
            case USER                  -> "user";
            case AI                    -> "assistant";
            case TOOL_EXECUTION_RESULT -> "tool";
            case CUSTOM                -> "custom";
        };
    }

    private Map<String, Object> event(String type, String id, Instant ts, Object body) {
        return map("id", UUID.randomUUID().toString(),
                   "type", type,
                   "timestamp", ts.toString(),
                   "body", body);
    }

    @SuppressWarnings("unchecked")
    private <K, V> Map<K, V> map(Object... kvPairs) {
        var m = new LinkedHashMap<K, V>();
        for (int i = 0; i < kvPairs.length; i += 2) {
            m.put((K) kvPairs[i], (V) kvPairs[i + 1]);
        }
        return m;
    }

    private void post(Object payload) throws Exception {
        var body = mapper.writeValueAsBytes(payload);
        var req  = HttpRequest.newBuilder()
                .uri(URI.create(ingestionUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", authHeader)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 300) {
            log.warn("Langfuse returned {}: {}", resp.statusCode(), resp.body());
        }
    }
}
