package com.filex.event;

/**
 * Base class for alert events.
 *
 * <p>Reserved for Phase 3 (Detection Engine).
 * Placeholder to establish event hierarchy.
 */
public abstract class AlertEvent extends AppEvent {

    protected AlertEvent(String source) {
        super(source);
    }

    protected AlertEvent(String source, String correlationId) {
        super(source, correlationId);
    }
}
