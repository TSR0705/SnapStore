package com.filex.event;

import java.time.Instant;

/**
 * Base interface for all events in the FileX system.
 *
 * <p>Events are immutable value objects that represent something that
 * has happened in the system. They flow through the EventBus to
 * decouple components.
 *
 * <p>All events must provide:
 * <ul>
 *   <li>Unique event ID for traceability</li>
 *   <li>Timestamp of when the event occurred</li>
 *   <li>Source identifier (component that created the event)</li>
 * </ul>
 */
public interface Event {

    /**
     * Returns the unique identifier for this event.
     * Used for tracing and correlation.
     */
    String getEventId();

    /**
     * Returns the timestamp when this event was created.
     */
    Instant getTimestamp();

    /**
     * Returns the source component that created this event.
     * Examples: "Bootstrap", "DatabaseManager", "ConfigManager"
     */
    String getSource();

    /**
     * Returns optional correlation ID for event chains.
     * Used to trace related events across the system.
     */
    default String getCorrelationId() {
        return null;
    }
}
