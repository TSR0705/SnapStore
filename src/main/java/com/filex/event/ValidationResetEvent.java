package com.filex.event;

/**
 * Event published when validation mode performs a bootstrap or runtime reset.
 * All subscribers (including UI controllers) should reset their transient states.
 */
public final class ValidationResetEvent extends AppEvent {

    public ValidationResetEvent(String source) {
        super(source);
    }

    public ValidationResetEvent(String source, String correlationId) {
        super(source, correlationId);
    }
}
