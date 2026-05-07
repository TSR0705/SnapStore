package com.filex.detection;

import com.filex.engine.MonitoringEvent;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Immutable context provided to detection rules during evaluation.
 *
 * <p>Provides access to:
 * <ul>
 *   <li>Current monitoring event being evaluated</li>
 *   <li>Recent event history for temporal correlation</li>
 *   <li>Time window boundaries</li>
 * </ul>
 *
 * <p>Thread-safe and immutable.
 */
public final class DetectionContext {

    private final MonitoringEvent currentEvent;
    private final List<MonitoringEvent> recentEvents;
    private final long windowMs;

    public DetectionContext(
            MonitoringEvent currentEvent,
            List<MonitoringEvent> recentEvents,
            long windowMs) {
        this.currentEvent = Objects.requireNonNull(currentEvent, "currentEvent must not be null");
        this.recentEvents = Collections.unmodifiableList(
                Objects.requireNonNull(recentEvents, "recentEvents must not be null"));
        this.windowMs = windowMs;
    }

    /**
     * Returns the current monitoring event being evaluated.
     */
    public MonitoringEvent currentEvent() {
        return currentEvent;
    }

    /**
     * Returns recent monitoring events within the time window.
     * List is immutable and ordered by occurrence time (oldest first).
     */
    public List<MonitoringEvent> recentEvents() {
        return recentEvents;
    }

    /**
     * Returns the time window size in milliseconds.
     */
    public long windowMs() {
        return windowMs;
    }

    /**
     * Returns events of a specific type within the time window.
     */
    public <T extends MonitoringEvent> List<T> getEventsOfType(Class<T> eventType) {
        return recentEvents.stream()
                .filter(eventType::isInstance)
                .map(eventType::cast)
                .collect(Collectors.toList());
    }

    /**
     * Counts events of a specific type within the time window.
     */
    public <T extends MonitoringEvent> int countEventsOfType(Class<T> eventType) {
        return (int) recentEvents.stream()
                .filter(eventType::isInstance)
                .count();
    }

    /**
     * Returns events that occurred within the specified time window
     * relative to the current event.
     */
    public List<MonitoringEvent> getEventsWithinWindow(long windowMs) {
        Instant cutoff = currentEvent.occurredAt().minusMillis(windowMs);
        return recentEvents.stream()
                .filter(e -> e.occurredAt().isAfter(cutoff))
                .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return "DetectionContext{" +
                "currentEvent=" + currentEvent.getClass().getSimpleName() +
                ", recentEventsCount=" + recentEvents.size() +
                ", windowMs=" + windowMs +
                '}';
    }
}
