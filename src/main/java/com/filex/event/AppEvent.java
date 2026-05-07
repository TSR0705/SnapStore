package com.filex.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Base class for all application domain events.
 *
 * <p>Events are immutable value objects that represent something that
 * has happened in the system. They flow through the EventBus to
 * decouple components.
 *
 * <p>All events provide:
 * <ul>
 *   <li>Unique event ID for traceability</li>
 *   <li>Timestamp of when the event occurred</li>
 *   <li>Source identifier (component that created the event)</li>
 *   <li>Optional correlation ID for event chains</li>
 * </ul>
 */
public abstract class AppEvent {

    private final String eventId;
    private final Instant occurredAt;
    private final String source;
    private final String correlationId;

    /**
     * @param source logical name of the component that produced this event
     */
    protected AppEvent(String source) {
        this(source, null);
    }

    /**
     * @param source logical name of the component that produced this event
     * @param correlationId optional correlation ID for event chains
     */
    protected AppEvent(String source, String correlationId) {
        this.eventId = UUID.randomUUID().toString();
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.occurredAt = Instant.now();
        this.correlationId = correlationId;
    }

    /** Unique identifier for this event. */
    public String eventId() {
        return eventId;
    }

    /** Timestamp at which this event was created. */
    public Instant occurredAt() {
        return occurredAt;
    }

    /** Logical name of the originating component. */
    public String source() {
        return source;
    }

    /** Optional correlation ID for event chains. */
    public String correlationId() {
        return correlationId;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "eventId='" + eventId + '\'' +
                ", source='" + source + '\'' +
                ", occurredAt=" + occurredAt +
                (correlationId != null ? ", correlationId='" + correlationId + '\'' : "") +
                '}';
    }
}

