package com.filex.event;

/**
 * Event published when the application runtime state changes.
 *
 * <p>Allows components to observe and react to lifecycle transitions
 * without tight coupling to the runtime manager.
 */
public final class RuntimeStateChangedEvent extends AppEvent {

    private final RuntimeState previousState;
    private final RuntimeState newState;

    public RuntimeStateChangedEvent(RuntimeState previousState, RuntimeState newState) {
        super("RuntimeManager");
        this.previousState = previousState;
        this.newState = newState;
    }

    public RuntimeState getPreviousState() {
        return previousState;
    }

    public RuntimeState getNewState() {
        return newState;
    }

    @Override
    public String toString() {
        return "RuntimeStateChangedEvent{" +
                "previousState=" + previousState +
                ", newState=" + newState +
                ", eventId='" + eventId() + '\'' +
                '}';
    }
}
