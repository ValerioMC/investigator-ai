package ai.investigator.agents.observability;

/**
 * Per-request context passed to the Langfuse listener via ThreadLocal.
 * Only carries grouping keys — sessionId groups every LLM call of one
 * investigation under the same Sessions entry in Langfuse; userId
 * attributes the run to a workspace/user.
 *
 * Works under virtual threads: each investigation runs end-to-end on one
 * virtual thread, so ThreadLocal is consistent.
 */
public final class LangfuseTraceContext {

    private static final ThreadLocal<String> SESSION = new ThreadLocal<>();
    private static final ThreadLocal<String> USER = new ThreadLocal<>();

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

    public static void clear() {
        SESSION.remove();
        USER.remove();
    }
}
