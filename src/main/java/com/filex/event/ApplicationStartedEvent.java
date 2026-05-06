package com.filex.event;

/**
 * Published once the application has fully bootstrapped and the
 * JavaFX stage is visible.
 *
 * <p>Subscribers can use this event to trigger deferred initialization
 * that must happen after the UI is ready (e.g., background service warm-up).
 */
public final class ApplicationStartedEvent extends AppEvent {

    public ApplicationStartedEvent() {
        super("Bootstrap");
    }
}
