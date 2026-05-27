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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Maps LangChain4j chat-model calls to Langfuse traces.
 *
 * One trace per agent invocation (not per LLM call). Agents with tools make
 * multiple LLM calls (decide-tool → execute → answer), all grouped under the
 * same trace via a per-thread stack. Nesting is handled correctly: Supervisor
 * sits at the bottom of the stack, subagents push/pop above it as they run.
 */
@Component
@ConditionalOnProperty(prefix = "langfuse", name = "enabled", havingValue = "true")
public class LangfuseObservabilityListener implements ChatModelListener {

    private static final Logger log = LoggerFactory.getLogger(LangfuseObservabilityListener.class);

    private static final String TRACE_ID       = "lf.traceId";
    private static final String GEN_ID         = "lf.genId";
    private static final String START_KEY      = "lf.startTime";
    private static final String SESSION_ID     = "lf.sessionId";
    private static final String USER_ID        = "lf.userId";
    private static final String IS_CONTINUATION = "lf.continuation";

    private final LangfuseClient client;

    public LangfuseObservabilityListener(LangfuseClient client) {
        this.client = client;
        log.info("Langfuse tracing enabled (per-call trace mode)");
    }

    @Override
    public void onRequest(ChatModelRequestContext ctx) {
        var messages = ctx.chatRequest().messages();
        // A continuation call has tool results injected into the message list by AiServices.
        boolean continuation = messages.stream().anyMatch(m -> m instanceof ToolExecutionResultMessage);

        String traceId;
        if (continuation) {
            traceId = LangfuseTraceContext.peekAgentTrace();
            if (traceId == null) traceId = UUID.randomUUID().toString(); // shouldn't happen
        } else {
            traceId = LangfuseTraceContext.pushAgentTrace();
        }

        ctx.attributes().put(TRACE_ID, traceId);
        ctx.attributes().put(GEN_ID, UUID.randomUUID().toString());
        ctx.attributes().put(START_KEY, Instant.now());
        ctx.attributes().put(IS_CONTINUATION, continuation);
        var sid = LangfuseTraceContext.session();
        if (sid != null) ctx.attributes().put(SESSION_ID, sid);
        var uid = LangfuseTraceContext.user();
        if (uid != null) ctx.attributes().put(USER_ID, uid);
    }

    @Override
    public void onResponse(ChatModelResponseContext ctx) {
        var traceId      = (String)  ctx.attributes().get(TRACE_ID);
        var genId        = (String)  ctx.attributes().get(GEN_ID);
        var startTime    = (Instant) ctx.attributes().get(START_KEY);
        var continuation = Boolean.TRUE.equals(ctx.attributes().get(IS_CONTINUATION));
        if (traceId == null) return;

        var req     = ctx.chatRequest();
        var resp    = ctx.chatResponse();
        var endTime = Instant.now();

        var ai        = resp.aiMessage();
        // hasToolExecutionRequests() == true → intermediate tool-call step, not the final answer
        boolean finalAnswer = ai == null || !ai.hasToolExecutionRequests();
        var output    = (ai != null && ai.text() != null) ? ai.text() : "";
        var usage     = resp.tokenUsage();
        var model     = resp.modelName() != null ? resp.modelName()
                        : req.modelName() != null ? req.modelName() : "mlx";
        var agentName = resolveAgentName(req.messages());
        var userInput = extractUserInput(req.messages());
        var sessionId = (String) ctx.attributes().get(SESSION_ID);
        var userId    = (String) ctx.attributes().get(USER_ID);

        var events = new ArrayList<Map<String, Object>>();

        // Emit trace-create on the first call of an invocation (sets input) and again
        // on the final call (adds output). Langfuse merges events with the same id, so
        // the second emission simply fills in the output field — no duplicate trace.
        if (!continuation || finalAnswer) {
            var traceBody = new LinkedHashMap<String, Object>();
            traceBody.put("id", traceId);
            traceBody.put("name", agentName);
            traceBody.put("timestamp", startTime.toString());
            traceBody.put("input", userInput);
            traceBody.put("tags", List.of("investigator-ai", agentName));
            if (sessionId != null) traceBody.put("sessionId", sessionId);
            if (userId != null)    traceBody.put("userId", userId);
            if (finalAnswer)       traceBody.put("output", output);
            events.add(event("trace-create", startTime, traceBody));
        }

        var genBody = new LinkedHashMap<String, Object>();
        genBody.put("id", genId);
        genBody.put("traceId", traceId);
        genBody.put("name", agentName + (continuation ? "#cont" : "#init"));
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
        events.add(event("generation-create", startTime, genBody));

        client.postEvents(events);

        if (finalAnswer) {
            LangfuseTraceContext.popAgentTrace();
        }
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
