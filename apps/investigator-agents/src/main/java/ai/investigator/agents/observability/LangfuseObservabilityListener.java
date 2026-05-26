package ai.investigator.agents.observability;

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

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Maps every LangChain4j chat-model call to a Langfuse trace+generation pair.
 *
 * <p>Each LLM call is its own standalone trace. Calls belonging to the same
 * investigation are grouped via {@code sessionId} (set by the resource layer
 * through {@link LangfuseTraceContext}), so the Langfuse Sessions tab shows
 * Supervisor + every subagent invocation under one entry.
 */
@Component
@ConditionalOnProperty(prefix = "langfuse", name = "enabled", havingValue = "true")
public class LangfuseObservabilityListener implements ChatModelListener {

    private static final Logger log = LoggerFactory.getLogger(LangfuseObservabilityListener.class);

    private static final String TRACE_ID = "lf.traceId";
    private static final String GEN_ID = "lf.genId";
    private static final String START_KEY = "lf.startTime";
    private static final String SESSION_ID = "lf.sessionId";
    private static final String USER_ID = "lf.userId";

    private final LangfuseClient client;

    public LangfuseObservabilityListener(LangfuseClient client) {
        this.client = client;
        log.info("Langfuse tracing enabled (per-call trace mode)");
    }

    @Override
    public void onRequest(ChatModelRequestContext ctx) {
        // Capture ThreadLocal values here — onResponse runs on a virtual thread
        // that does not inherit ThreadLocals.
        ctx.attributes().put(TRACE_ID, UUID.randomUUID().toString());
        ctx.attributes().put(GEN_ID, UUID.randomUUID().toString());
        ctx.attributes().put(START_KEY, Instant.now());
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

        var agentName = resolveAgentName(req.messages());
        var userInput = extractUserInput(req.messages());
        // text() returns null when the assistant message contains only tool_calls
        var output    = (resp.aiMessage() != null && resp.aiMessage().text() != null) ? resp.aiMessage().text() : "";
        var usage     = resp.tokenUsage();
        var model     = resp.modelName() != null ? resp.modelName()
                        : req.modelName() != null ? req.modelName() : "mlx";

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
        if (userId != null)    traceBody.put("userId", userId);

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
            genBody.put("usage", Map.of(
                "input",  usage.inputTokenCount(),
                "output", usage.outputTokenCount(),
                "total",  usage.totalTokenCount()
            ));
        }

        client.postEvents(List.of(
            event("trace-create", startTime, traceBody),
            event("generation-create", startTime, genBody)
        ));
    }

    @Override
    public void onError(ChatModelErrorContext ctx) {
        var traceId = (String) ctx.attributes().get(TRACE_ID);
        if (traceId == null) return;
        var body = new LinkedHashMap<String, Object>();
        body.put("id", traceId);
        body.put("name", "LLMCall");
        body.put("timestamp", Instant.now().toString());
        body.put("level", "ERROR");
        body.put("statusMessage", ctx.error().getMessage());
        var sid = (String) ctx.attributes().get(SESSION_ID);
        if (sid != null) body.put("sessionId", sid);
        client.postEvents(List.of(event("trace-create", Instant.now(), body)));
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
                    if (text.contains("investigative journalism supervisor")) return "SupervisorAgent";
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

    private Map<String, Object> event(String type, Instant ts, Map<String, Object> body) {
        var ev = new LinkedHashMap<String, Object>();
        ev.put("id", UUID.randomUUID().toString());
        ev.put("type", type);
        ev.put("timestamp", ts.toString());
        ev.put("body", body);
        return ev;
    }
}
