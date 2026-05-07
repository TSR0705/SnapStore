package com.filex.runtime;

import com.filex.event.EventBus;
import com.filex.event.RuntimeState;
import com.filex.event.RuntimeStateChangedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RuntimeManager.
 */
class RuntimeManagerTest {

    private EventBus eventBus;
    private RuntimeManager runtimeManager;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        runtimeManager = new RuntimeManager(eventBus);
    }

    @AfterEach
    void tearDown() {
        if (eventBus != null) {
            eventBus.shutdown();
        }
    }

    @Test
    void testInitialState() {
        assertEquals(RuntimeState.INITIALIZING, runtimeManager.getCurrentState());
    }

    @Test
    void testValidTransition() {
        // Given
        List<RuntimeStateChangedEvent> events = new ArrayList<>();
        eventBus.subscribe(RuntimeStateChangedEvent.class, events::add);

        // When
        runtimeManager.transitionTo(RuntimeState.STARTING);

        // Then
        assertEquals(RuntimeState.STARTING, runtimeManager.getCurrentState());
        assertEquals(1, events.size());
        assertEquals(RuntimeState.INITIALIZING, events.get(0).getPreviousState());
        assertEquals(RuntimeState.STARTING, events.get(0).getNewState());
    }

    @Test
    void testInvalidTransition() {
        // When/Then
        assertThrows(IllegalStateException.class, () ->
                runtimeManager.transitionTo(RuntimeState.STOPPED));
    }

    @Test
    void testFullLifecycle() {
        // When
        runtimeManager.transitionTo(RuntimeState.STARTING);
        runtimeManager.transitionTo(RuntimeState.RUNNING);
        runtimeManager.transitionTo(RuntimeState.STOPPING);
        runtimeManager.transitionTo(RuntimeState.STOPPED);

        // Then
        assertEquals(RuntimeState.STOPPED, runtimeManager.getCurrentState());
    }

    @Test
    void testFailedState() {
        // When
        runtimeManager.transitionTo(RuntimeState.FAILED);

        // Then
        assertEquals(RuntimeState.FAILED, runtimeManager.getCurrentState());

        // Failed is terminal
        assertThrows(IllegalStateException.class, () ->
                runtimeManager.transitionTo(RuntimeState.RUNNING));
    }

    @Test
    void testIdempotentTransition() {
        // When
        runtimeManager.transitionTo(RuntimeState.STARTING);
        runtimeManager.transitionTo(RuntimeState.STARTING); // Same state

        // Then
        assertEquals(RuntimeState.STARTING, runtimeManager.getCurrentState());
    }
}
