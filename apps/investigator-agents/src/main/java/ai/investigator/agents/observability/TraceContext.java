package ai.investigator.agents.observability;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Holds the active Langfuse trace ID for the current thread during an investigation.
 * Set by InvestigationOrchestrator; read by LangfuseObservabilityListener so that
 * all sub-agent LLM calls are recorded as child generations under the same trace
 * instead of creating independent top-level traces.
 */
public class TraceContext {

    public record Context(String traceId, String query, AtomicBoolean traceCreated) {
        public Context(String traceId, String query) {
            this(traceId, query, new AtomicBoolean(false));
        }
    }

    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    public static void set(String traceId, String query) {
        CURRENT.set(new Context(traceId, query));
    }

    public static Context get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
