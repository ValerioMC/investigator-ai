package ai.investigator.agents.observability;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/**
 * Per-request context passed to the Langfuse listener via ThreadLocal.
 * sessionId groups every LLM call of one investigation under the same
 * Sessions entry in Langfuse.
 *
 * TRACE_STACK tracks active agent invocations on the current virtual thread.
 * Each new agent invocation pushes a traceId; the final LLM call of that
 * invocation pops it. The stack handles nesting: Supervisor on the bottom,
 * subagents pushed/popped above it as they run.
 */
public final class LangfuseTraceContext {

    private static final ThreadLocal<String> SESSION = new ThreadLocal<>();
    private static final ThreadLocal<String> USER    = new ThreadLocal<>();
    private static final ThreadLocal<Deque<String>> TRACE_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    private LangfuseTraceContext() {}

    public static void setSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) SESSION.remove();
        else SESSION.set(sessionId);
    }

    public static String session() { return SESSION.get(); }

    public static void setUser(String userId) {
        if (userId == null || userId.isBlank()) USER.remove();
        else USER.set(userId);
    }

    public static String user() { return USER.get(); }

    /** Push a new traceId for a new agent invocation. Returns the generated id. */
    public static String pushAgentTrace() {
        var id = UUID.randomUUID().toString();
        TRACE_STACK.get().push(id);
        return id;
    }

    /** Peek at the current (innermost) agent traceId without removing it. */
    public static String peekAgentTrace() {
        return TRACE_STACK.get().peek();
    }

    /** Pop the current agent traceId when the invocation completes. */
    public static String popAgentTrace() {
        return TRACE_STACK.get().poll();
    }

    public static void clear() {
        SESSION.remove();
        USER.remove();
        TRACE_STACK.remove();
    }
}
