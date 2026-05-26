package ai.investigator.agents.observability;

/**
 * Per-request context carried into the Langfuse listener via ThreadLocal.
 *
 * <ul>
 *   <li>{@code sessionId} groups every trace of one investigation under the same
 *       Sessions entry in Langfuse.</li>
 *   <li>{@code userId} attributes the run to a workspace/user.</li>
 *   <li>{@code parentTraceId} is the umbrella trace created by the orchestrator;
 *       when set, the LLM listener attaches each generation as an observation of
 *       that trace instead of creating its own, so Langfuse renders the six
 *       subagent steps as children of one "Investigation" trace.</li>
 *   <li>{@code parentObservationId} is the immediate parent span for a given
 *       LLM call — used by the orchestrator to wrap each subagent step in a
 *       named span, so the UI tree looks like
 *       Investigation → step-corporate → CorporateAgent (generation).</li>
 * </ul>
 *
 * Works with virtual threads — ThreadLocals are still per-thread, and each
 * investigation runs end-to-end on a single virtual thread.
 */
public final class LangfuseTraceContext {

    private static final ThreadLocal<String> SESSION = new ThreadLocal<>();
    private static final ThreadLocal<String> USER = new ThreadLocal<>();
    private static final ThreadLocal<String> PARENT_TRACE = new ThreadLocal<>();
    private static final ThreadLocal<String> PARENT_OBSERVATION = new ThreadLocal<>();

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

    public static void setParentTrace(String traceId) {
        if (traceId == null || traceId.isBlank()) PARENT_TRACE.remove();
        else PARENT_TRACE.set(traceId);
    }

    public static String parentTrace() { return PARENT_TRACE.get(); }

    public static void setParentObservation(String observationId) {
        if (observationId == null || observationId.isBlank()) PARENT_OBSERVATION.remove();
        else PARENT_OBSERVATION.set(observationId);
    }

    public static String parentObservation() { return PARENT_OBSERVATION.get(); }

    public static void clear() {
        SESSION.remove();
        USER.remove();
        PARENT_TRACE.remove();
        PARENT_OBSERVATION.remove();
    }
}
