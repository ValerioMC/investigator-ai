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
 * Bridges LangChain4j {@code ChatModelListener} events into Langfuse ingestion
 * events.
 *
 * <p>If a parent traceId is present in {@link LangfuseTraceContext}, the
 * listener attaches each LLM call as a {@code generation} observation under
 * that trace (and optionally as a child of a span the orchestrator opened) —
 * the orchestrator owns the umbrella trace lifecycle.
 *
 * <p>If no parent traceId is set, the listener falls back to creating its own
 * one-off trace per LLM call (backwards-compatible behaviour).
 */
@Component
@ConditionalOnProperty(prefix = "langfuse", name = "enabled", havingValue = "true")
public class LangfuseObservabilityListener implements ChatModelListener {

    private static final Logger log = LoggerFactory.getLogger(LangfuseObservabilityListener.class);

    private static final String TRACE_ID      = "lf.traceId";
    private static final String GEN_ID        = "lf.genId";
    private static final String START_KEY     = "lf.startTime";
    private static final String SESSION_ID    = "lf.sessionId";
    private static final String USER_ID       = "lf.userId";
    private static final String PARENT_TRACE  = "lf.parentTrace";
    private static final String PARENT_OBS    = "lf.parentObservation";

    private final LangfuseClient client;

    public LangfuseObservabilityListener(LangfuseClient client) {
        this.client = client;
        log.info("Langfuse tracing enabled via LangfuseClient");
    }

    @Override
    public void onRequest(ChatModelRequestContext ctx) {
        // Capture per-thread context now, because onResponse will run on a
        // virtual thread that doesn't inherit ThreadLocals.
        String parentTrace = LangfuseTraceContext.parentTrace();
        String parentObs   = LangfuseTraceContext.parentObservation();

        ctx.attributes().put(TRACE_ID,  parentTrace != null ? parentTrace : UUID.randomUUID().toString());
        ctx.attributes().put(GEN_ID,    UUID.randomUUID().toString());
        ctx.attributes().put(START_KEY, Instant.now());
        if (parentTrace != null) ctx.attributes().put(PARENT_TRACE, Boolean.TRUE);
        if (parentObs   != null) ctx.attributes().put(PARENT_OBS, parentObs);

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
        var output    = resp.aiMessage() != null ? resp.aiMessage().text() : "";
        var usage     = resp.tokenUsage();
        var model     = resp.modelName() != null ? resp.modelName()
                        : req.modelName() != null ? req.modelName() : "ollama";

        var sessionId   = (String)  ctx.attributes().get(SESSION_ID);
        var userId      = (String)  ctx.attributes().get(USER_ID);
        var parentObs   = (String)  ctx.attributes().get(PARENT_OBS);
        boolean nested  = Boolean.TRUE.equals(ctx.attributes().get(PARENT_TRACE));

        var genBody = new LinkedHashMap<String, Object>();
        genBody.put("id", genId);
        genBody.put("traceId", traceId);
        if (parentObs != null) genBody.put("parentObservationId", parentObs);
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
        var generationEvent = event("generation-create", startTime, genBody);

        if (nested) {
            // Parent trace is owned by the orchestrator — only emit the generation.
            client.postEvents(List.of(generationEvent));
        } else {
            // Fallback: create a standalone trace per LLM call.
            var traceBody = new LinkedHashMap<String, Object>();
            traceBody.put("id", traceId);
            traceBody.put("name", agentName);
            traceBody.put("timestamp", startTime.toString());
            traceBody.put("input", userInput);
            traceBody.put("output", output);
            traceBody.put("tags", List.of("investigator-ai", agentName));
            if (sessionId != null) traceBody.put("sessionId", sessionId);
            if (userId != null)    traceBody.put("userId", userId);
            client.postEvents(List.of(
                event("trace-create", startTime, traceBody),
                generationEvent
            ));
        }
    }

    @Override
    public void onError(ChatModelErrorContext ctx) {
        var traceId = (String) ctx.attributes().get(TRACE_ID);
        if (traceId == null) return;
        boolean nested = Boolean.TRUE.equals(ctx.attributes().get(PARENT_TRACE));
        if (nested) {
            // Don't pollute the umbrella trace; the orchestrator will record the failure.
            log.warn("LLM call failed under parent trace {}: {}", traceId, ctx.error().getMessage());
            return;
        }
        var body = new LinkedHashMap<String, Object>();
        body.put("id", traceId);
        body.put("name", "investigator-ai");
        body.put("timestamp", Instant.now().toString());
        body.put("level", "ERROR");
        body.put("statusMessage", ctx.error().getMessage());
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

    private Map<String, Object> event(String type, Instant ts, Map<String, Object> body) {
        var ev = new LinkedHashMap<String, Object>();
        ev.put("id", UUID.randomUUID().toString());
        ev.put("type", type);
        ev.put("timestamp", ts.toString());
        ev.put("body", body);
        return ev;
    }
}
