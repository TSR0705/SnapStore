package com.filex.event;

/**
 * Base class for file system events.
 *
 * <p>Reserved for Phase 2 (Monitoring Engine).
 * Placeholder to establish event hierarchy.
 */
public abstract class FileEvent extends AppEvent {

    protected FileEvent(String source) {
        super(source);
    }

    protected FileEvent(String source, String correlationId) {
        super(source, correlationId);
    }
}
