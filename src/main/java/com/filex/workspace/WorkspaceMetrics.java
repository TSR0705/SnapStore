package com.filex.workspace;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe metrics for investigation workspace operations.
 *
 * <p>Tracks:
 * <ul>
 *   <li>Navigation operations and latency</li>
 *   <li>Replay render operations and latency</li>
 *   <li>State synchronization operations</li>
 *   <li>Session restoration operations</li>
 *   <li>Async query coordination</li>
 *   <li>Failure counts</li>
 * </ul>
 *
 * <p>All counters use {@link AtomicLong} for thread-safe updates.
 * Metrics are lightweight and have minimal performance impact.
 *
 * <p>Thread-safety: All operations are atomic.
 */
public final class WorkspaceMetrics {

    // Navigation metrics
    private final AtomicLong navigationOperations = new AtomicLong(0);
    private final AtomicLong navigationLatencyMs = new AtomicLong(0);
    private final AtomicLong navigationFailures = new AtomicLong(0);

    // Replay metrics
    private final AtomicLong replayRenderOperations = new AtomicLong(0);
    private final AtomicLong replayRenderLatencyMs = new AtomicLong(0);
    private final AtomicLong replayInterruptions = new AtomicLong(0);

    // State synchronization metrics
    private final AtomicLong stateSynchronizations = new AtomicLong(0);
    private final AtomicLong staleStateDetections = new AtomicLong(0);

    // Session metrics
    private final AtomicLong sessionRestorations = new AtomicLong(0);
    private final AtomicLong sessionRestorationLatencyMs = new AtomicLong(0);
    private final AtomicLong sessionRestorationFailures = new AtomicLong(0);

    // Async query metrics
    private final AtomicLong asyncQueriesDispatched = new AtomicLong(0);
    private final AtomicLong asyncQueriesCompleted = new AtomicLong(0);
    private final AtomicLong asyncQueriesFailed = new AtomicLong(0);
    private final AtomicLong staleAsyncResponsesIgnored = new AtomicLong(0);

    // -------------------------------------------------------------------------
    // Navigation metrics
    // -------------------------------------------------------------------------

    public void recordNavigation(long latencyMs) {
        navigationOperations.incrementAndGet();
        navigationLatencyMs.addAndGet(latencyMs);
    }

    public void recordNavigationFailure() {
        navigationFailures.incrementAndGet();
    }

    // -------------------------------------------------------------------------
    // Replay metrics
    // -------------------------------------------------------------------------

    public void recordReplayRender(long latencyMs) {
        replayRenderOperations.incrementAndGet();
        replayRenderLatencyMs.addAndGet(latencyMs);
    }

    public void recordReplayInterruption() {
        replayInterruptions.incrementAndGet();
    }

    // -------------------------------------------------------------------------
    // State synchronization metrics
    // -------------------------------------------------------------------------

    public void recordStateSynchronization() {
        stateSynchronizations.incrementAndGet();
    }

    public void recordStaleStateDetection() {
        staleStateDetections.incrementAndGet();
    }

    // -------------------------------------------------------------------------
    // Session metrics
    // -------------------------------------------------------------------------

    public void recordSessionRestoration(long latencyMs) {
        sessionRestorations.incrementAndGet();
        sessionRestorationLatencyMs.addAndGet(latencyMs);
    }

    public void recordSessionRestorationFailure() {
        sessionRestorationFailures.incrementAndGet();
    }

    // -------------------------------------------------------------------------
    // Async query metrics
    // -------------------------------------------------------------------------

    public void recordAsyncQueryDispatched() {
        asyncQueriesDispatched.incrementAndGet();
    }

    public void recordAsyncQueryCompleted() {
        asyncQueriesCompleted.incrementAndGet();
    }

    public void recordAsyncQueryFailed() {
        asyncQueriesFailed.incrementAndGet();
    }

    public void recordStaleAsyncResponseIgnored() {
        staleAsyncResponsesIgnored.incrementAndGet();
    }

    // -------------------------------------------------------------------------
    // Snapshot
    // -------------------------------------------------------------------------

    /**
     * Returns an immutable snapshot of current metrics.
     */
    public WorkspaceMetricsSnapshot snapshot() {
        return new WorkspaceMetricsSnapshot(
                navigationOperations.get(),
                navigationLatencyMs.get(),
                navigationFailures.get(),
                replayRenderOperations.get(),
                replayRenderLatencyMs.get(),
                replayInterruptions.get(),
                stateSynchronizations.get(),
                staleStateDetections.get(),
                sessionRestorations.get(),
                sessionRestorationLatencyMs.get(),
                sessionRestorationFailures.get(),
                asyncQueriesDispatched.get(),
                asyncQueriesCompleted.get(),
                asyncQueriesFailed.get(),
                staleAsyncResponsesIgnored.get()
        );
    }

    /**
     * Immutable snapshot of workspace metrics.
     */
    public record WorkspaceMetricsSnapshot(
            long navigationOperations,
            long navigationLatencyMs,
            long navigationFailures,
            long replayRenderOperations,
            long replayRenderLatencyMs,
            long replayInterruptions,
            long stateSynchronizations,
            long staleStateDetections,
            long sessionRestorations,
            long sessionRestorationLatencyMs,
            long sessionRestorationFailures,
            long asyncQueriesDispatched,
            long asyncQueriesCompleted,
            long asyncQueriesFailed,
            long staleAsyncResponsesIgnored
    ) {
        public double averageNavigationLatencyMs() {
            return navigationOperations > 0 ? (double) navigationLatencyMs / navigationOperations : 0.0;
        }

        public double averageReplayRenderLatencyMs() {
            return replayRenderOperations > 0 ? (double) replayRenderLatencyMs / replayRenderOperations : 0.0;
        }

        public double averageSessionRestorationLatencyMs() {
            return sessionRestorations > 0 ? (double) sessionRestorationLatencyMs / sessionRestorations : 0.0;
        }

        public long pendingAsyncQueries() {
            return asyncQueriesDispatched - asyncQueriesCompleted - asyncQueriesFailed;
        }
    }
}
