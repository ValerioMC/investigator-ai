package ai.investigator.agents.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
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
    private static final String IS_CHILD   = "lf.isChild";
    private static final String TRACE_CTX  = "lf.traceCtx";  // captured on request thread, used in virtual thread

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
        var traceCtx = TraceContext.get();
        if (traceCtx != null) {
            // nested call inside an orchestrated investigation — use the shared trace ID.
            // Store the context reference now (on the calling thread) because virtual threads
            // created later in onResponse do not inherit ThreadLocal values.
            ctx.attributes().put(TRACE_ID,  traceCtx.traceId());
            ctx.attributes().put(IS_CHILD,  true);
            ctx.attributes().put(TRACE_CTX, traceCtx);
        } else {
            // standalone call — own trace
            ctx.attributes().put(TRACE_ID,  UUID.randomUUID().toString());
            ctx.attributes().put(IS_CHILD,  false);
        }
        ctx.attributes().put(GEN_ID,    UUID.randomUUID().toString());
        ctx.attributes().put(START_KEY, Instant.now());
    }

    @Override
    public void onResponse(ChatModelResponseContext ctx) {
        var traceId   = (String)  ctx.attributes().get(TRACE_ID);
        var genId     = (String)  ctx.attributes().get(GEN_ID);
        var startTime = (Instant) ctx.attributes().get(START_KEY);
        var isChild   = Boolean.TRUE.equals(ctx.attributes().get(IS_CHILD));
        if (traceId == null) return;

        var req     = ctx.chatRequest();
        var resp    = ctx.chatResponse();
        var endTime = Instant.now();

        Thread.ofVirtual().start(() -> {
            try {
                var agentName = resolveAgentName(req.messages());
                var userInput = extractUserInput(req.messages());
                // text() is null when the model emits a tool call instead of a text response
                var output    = resp.aiMessage() != null && resp.aiMessage().text() != null
                                ? resp.aiMessage().text() : "";
                var usage     = resp.tokenUsage();
                var model     = resp.modelName() != null ? resp.modelName()
                                : req.modelName() != null ? req.modelName() : "ollama";

                var genBody = new LinkedHashMap<String, Object>();
                genBody.put("id",        genId);
                genBody.put("traceId",   traceId);
                genBody.put("name",      agentName);
                genBody.put("startTime", startTime.toString());
                genBody.put("endTime",   endTime.toString());
                genBody.put("model",     model);
                genBody.put("input",     messagesAsJson(req.messages()));
                genBody.put("output",    Map.of("role", "assistant", "content", output));
                if (usage != null) {
                    genBody.put("usage", map(
                            "input",  usage.inputTokenCount(),
                            "output", usage.outputTokenCount(),
                            "total",  usage.totalTokenCount()
                    ));
                }

                List<Object> events;
                if (isChild) {
                    // first child call for this trace: also create the trace record.
                    // Use the reference stored in onRequest — TraceContext ThreadLocal is
                    // not visible here because virtual threads don't inherit ThreadLocals.
                    var traceCtx = (TraceContext.Context) ctx.attributes().get(TRACE_CTX);
                    if (traceCtx != null && traceCtx.traceCreated().compareAndSet(false, true)) {
                        var traceBody = map(
                                "id",        traceId,
                                "name",      "investigator-ai",
                                "timestamp", startTime.toString(),
                                "input",     traceCtx.query(),
                                "tags",      List.of("investigator-ai")
                        );
                        events = List.of(
                                event("trace-create",      traceId, startTime, traceBody),
                                event("generation-create", genId,   startTime, genBody)
                        );
                    } else {
                        events = List.of(event("generation-create", genId, startTime, genBody));
                    }
                } else {
                    // standalone call: own trace
                    var traceBody = map(
                            "id",        traceId,
                            "name",      "investigator-ai",
                            "timestamp", startTime.toString(),
                            "input",     userInput,
                            "output",    output,
                            "tags",      List.of("investigator-ai", agentName)
                    );
                    events = List.of(
                            event("trace-create",      traceId, startTime, traceBody),
                            event("generation-create", genId,   startTime, genBody)
                    );
                }

                post(Map.of("batch", events));
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
                            "statusMessage", ctx.error().getMessage())))));
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
                    if (text.contains("corporate intelligence"))               return "CorporateAgent";
                    if (text.contains("person intelligence"))                  return "PersonProfileAgent";
                    if (text.contains("financial forensics"))                  return "FinancialFlowAgent";
                    if (text.contains("document intelligence"))                return "DocumentAgent";
                    if (text.contains("source verification"))                  return "SourceVerificationAgent";
                    if (text.contains("investigative journalism supervisor"))   return "SupervisorAgent";
                    if (text.contains("report formatter"))                     return "ReportFormatter";
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
            case SystemMessage sm              -> sm.text();
            case UserMessage um                -> um.hasSingleText() ? um.singleText() : um.toString();
            case AiMessage am                  -> am.text() != null ? am.text() : "";
            case ToolExecutionResultMessage tr -> tr.text() != null ? tr.text() : "";
            default                            -> "";
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
