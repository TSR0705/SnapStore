package com.filex.event;

/**
 * Published when the application begins its shutdown sequence.
 *
 * <p>Subscribers should use this event to release resources, flush
 * buffers, and cancel pending work. The event is published before
 * the database and executor services are closed.
 */
public final class ApplicationShutdownEvent extends AppEvent {

    public ApplicationShutdownEvent() {
        super("Bootstrap");
    }
}
