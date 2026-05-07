package com.filex.event;

/**
 * Immutable snapshot of EventBus runtime metrics.
 *
 * <p>Provides observability into EventBus state for monitoring
 * and diagnostics without heavy framework dependencies.
 */
public final class EventBusMetrics {

    private final int queueSize;
    private final int queueCapacity;
    private final int totalSubscribers;
    private final int activeDispatcherThreads;
    private final long totalPublishedEvents;
    private final long totalAsyncEvents;
    private final long totalDroppedEvents;
    private final long totalDispatchFailures;
    private final boolean isShutdown;

    public EventBusMetrics(
            int queueSize,
            int queueCapacity,
            int totalSubscribers,
            int activeDispatcherThreads,
            long totalPublishedEvents,
            long totalAsyncEvents,
            long totalDroppedEvents,
            long totalDispatchFailures,
            boolean isShutdown) {
        this.queueSize = queueSize;
        this.queueCapacity = queueCapacity;
        this.totalSubscribers = totalSubscribers;
        this.activeDispatcherThreads = activeDispatcherThreads;
        this.totalPublishedEvents = totalPublishedEvents;
        this.totalAsyncEvents = totalAsyncEvents;
        this.totalDroppedEvents = totalDroppedEvents;
        this.totalDispatchFailures = totalDispatchFailures;
        this.isShutdown = isShutdown;
    }

    /** Current number of events in the async dispatch queue. */
    public int getQueueSize() {
        return queueSize;
    }

    /** Maximum capacity of the async dispatch queue. */
    public int getQueueCapacity() {
        return queueCapacity;
    }

    /** Total number of registered subscribers across all event types. */
    public int getTotalSubscribers() {
        return totalSubscribers;
    }

    /** Number of active dispatcher threads. */
    public int getActiveDispatcherThreads() {
        return activeDispatcherThreads;
    }

    /** Total number of events published (sync + async). */
    public long getTotalPublishedEvents() {
        return totalPublishedEvents;
    }

    /** Total number of async events queued. */
    public long getTotalAsyncEvents() {
        return totalAsyncEvents;
    }

    /** Total number of events dropped due to queue overflow. */
    public long getTotalDroppedEvents() {
        return totalDroppedEvents;
    }

    /** Total number of dispatch failures (subscriber exceptions). */
    public long getTotalDispatchFailures() {
        return totalDispatchFailures;
    }

    /** Whether the EventBus is shutdown. */
    public boolean isShutdown() {
        return isShutdown;
    }

    /** Queue utilization as a percentage (0-100). */
    public double getQueueUtilization() {
        return queueCapacity > 0 ? (queueSize * 100.0 / queueCapacity) : 0.0;
    }

    @Override
    public String toString() {
        return "EventBusMetrics{" +
                "queueSize=" + queueSize +
                ", queueCapacity=" + queueCapacity +
                ", queueUtilization=" + String.format("%.1f%%", getQueueUtilization()) +
                ", totalSubscribers=" + totalSubscribers +
                ", activeDispatcherThreads=" + activeDispatcherThreads +
                ", totalPublishedEvents=" + totalPublishedEvents +
                ", totalAsyncEvents=" + totalAsyncEvents +
                ", totalDroppedEvents=" + totalDroppedEvents +
                ", totalDispatchFailures=" + totalDispatchFailures +
                ", isShutdown=" + isShutdown +
                '}';
    }
}
