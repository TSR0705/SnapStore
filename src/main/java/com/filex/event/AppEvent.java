package com.filex.event;

import java.time.Instant;
import java.util.Objects;

/**
 * Base class for all application domain events.
 *
 * <p>Events are immutable value objects. Subclasses add domain-specific
 * payload. The {@link EventBus} routes events to registered subscribers
 * by exact type and supertype.
 *
 * <p>Phase 1A defines only structural events. Detection, alert, and
 * monitoring events are reserved for later phases.
 */
public abstract class AppEvent {

    private final Instant occurredAt;
    private final String source;

    /**
     * @param source logical name of the component that produced this event
     */
    protected AppEvent(String source) {
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.occurredAt = Instant.now();
    }

    /** Timestamp at which this event was created. */
    public Instant occurredAt() {
        return occurredAt;
    }

    /** Logical name of the originating component. */
    public String source() {
        return source;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[source=" + source + ", at=" + occurredAt + "]";
    }
}
