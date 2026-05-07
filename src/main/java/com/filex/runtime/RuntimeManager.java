package com.filex.runtime;

import com.filex.event.EventBus;
import com.filex.event.RuntimeState;
import com.filex.event.RuntimeStateChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the runtime state of the FileX application.
 *
 * <p>Tracks state transitions and publishes {@link RuntimeStateChangedEvent}
 * to allow components to observe lifecycle changes without tight coupling.
 *
 * <p>Thread-safe state transitions with proper synchronization.
 */
public final class RuntimeManager {

    private static final Logger log = LoggerFactory.getLogger(RuntimeManager.class);

    private final EventBus eventBus;
    private volatile RuntimeState currentState;

    public RuntimeManager(EventBus eventBus) {
        this.eventBus = eventBus;
        this.currentState = RuntimeState.INITIALIZING;
        log.info("RuntimeManager created in state: {}", currentState);
    }

    /**
     * Returns the current runtime state.
     */
    public RuntimeState getCurrentState() {
        return currentState;
    }

    /**
     * Transitions to a new runtime state.
     *
     * @param newState the target state
     * @throws IllegalStateException if transition is invalid
     */
    public synchronized void transitionTo(RuntimeState newState) {
        if (newState == null) {
            throw new IllegalArgumentException("newState must not be null");
        }

        if (currentState == newState) {
            log.debug("Already in state: {}", newState);
            return;
        }

        // Validate transition
        if (!isValidTransition(currentState, newState)) {
            throw new IllegalStateException(
                    "Invalid state transition: " + currentState + " -> " + newState);
        }

        RuntimeState previousState = currentState;
        currentState = newState;

        log.info("Runtime state changed: {} -> {}", previousState, newState);

        // Publish state change event
        try {
            eventBus.publish(new RuntimeStateChangedEvent(previousState, newState));
        } catch (Exception e) {
            log.error("Failed to publish RuntimeStateChangedEvent", e);
        }
    }

    /**
     * Validates if a state transition is allowed.
     */
    private boolean isValidTransition(RuntimeState from, RuntimeState to) {
        return switch (from) {
            case INITIALIZING -> to == RuntimeState.STARTING || to == RuntimeState.FAILED;
            case STARTING -> to == RuntimeState.RUNNING || to == RuntimeState.FAILED;
            case RUNNING -> to == RuntimeState.STOPPING || to == RuntimeState.FAILED;
            case STOPPING -> to == RuntimeState.STOPPED || to == RuntimeState.FAILED;
            case STOPPED -> false; // Terminal state
            case FAILED -> false; // Terminal state
        };
    }
}
