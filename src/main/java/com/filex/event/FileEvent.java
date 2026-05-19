package com.filex.event;

/**
 * Base class for file system events.
 */
public abstract class FileEvent extends AppEvent {

    protected FileEvent(String source) {
        super(source);
    }

    protected FileEvent(String source, String correlationId) {
        super(source, correlationId);
    }
}
