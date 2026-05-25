package ai.investigator.agents.observability;

/**
 * Per-request context carried into the Langfuse listener via a ThreadLocal.
 * The orchestrator sets a sessionId before running the investigation so every
 * LLM call (and therefore every trace) ends up linked under the same Langfuse
 * session. Without this, each generation gets its own one-off trace and the
 * Sessions tab in Langfuse stays empty.
 *
 * Works with virtual threads — ThreadLocals are still per-thread, and each
 * investigation runs end-to-end on a single virtual thread.
 */
public final class LangfuseTraceContext {

    private static final ThreadLocal<String> SESSION = new ThreadLocal<>();
    private static final ThreadLocal<String> USER = new ThreadLocal<>();

    private LangfuseTraceContext() {}

    public static void setSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) SESSION.remove();
        else SESSION.set(sessionId);
    }

    public static String session() {
        return SESSION.get();
    }

    public static void setUser(String userId) {
        if (userId == null || userId.isBlank()) USER.remove();
        else USER.set(userId);
    }

    public static String user() {
        return USER.get();
    }

    public static void clear() {
        SESSION.remove();
        USER.remove();
    }
}
