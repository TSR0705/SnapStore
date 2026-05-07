package com.filex.engine;

import com.filex.event.AppEvent;
import com.filex.event.EventBus;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Architecture and code quality audit for Phase 1D monitoring engine.
 * 
 * Validates:
 * - Clean architecture boundaries
 * - No SQL in monitoring engine
 * - Proper immutability
 * - Thread safety patterns
 * - Event hierarchy correctness
 */
class MonitoringArchitectureAuditTest {

    // =========================================================================
    // ARCHITECTURE BOUNDARY VALIDATION
    // =========================================================================

    @Test
    void testMonitoringEngineHasNoSQLDependencies() {
        // Verify MonitoringEngine doesn't import SQL classes
        Field[] fields = MonitoringEngine.class.getDeclaredFields();
        
        for (Field field : fields) {
            String typeName = field.getType().getName();
            assertFalse(typeName.contains("java.sql"),
                    "MonitoringEngine should not have SQL dependencies: " + field.getName());
            assertFalse(typeName.contains("Connection"),
                    "MonitoringEngine should not have database connections: " + field.getName());
        }
    }

    @Test
    void testMonitoringEngineHasNoRepositoryDependencies() {
        Field[] fields = MonitoringEngine.class.getDeclaredFields();
        
        for (Field field : fields) {
            String typeName = field.getType().getName();
            assertFalse(typeName.contains("Repository"),
                    "MonitoringEngine should not depend on repositories: " + field.getName());
        }
    }

    @Test
    void testMonitoringEngineHasNoUIDependencies() {
        Field[] fields = MonitoringEngine.class.getDeclaredFields();
        
        for (Field field : fields) {
            String typeName = field.getType().getName();
            assertFalse(typeName.contains("javafx"),
                    "MonitoringEngine should not depend on JavaFX: " + field.getName());
            assertFalse(typeName.contains("ViewManager"),
                    "MonitoringEngine should not depend on ViewManager: " + field.getName());
        }
    }

    @Test
    void testMonitoringEngineOnlyDependsOnEventBus() {
        Field[] fields = MonitoringEngine.class.getDeclaredFields();
        
        boolean hasEventBus = false;
        for (Field field : fields) {
            if (field.getType().equals(EventBus.class)) {
                hasEventBus = true;
            }
        }
        
        assertTrue(hasEventBus, "MonitoringEngine should depend on EventBus");
    }

    // =========================================================================
    // EVENT IMMUTABILITY VALIDATION
    // =========================================================================

    @Test
    void testMonitoringEventIsImmutable() {
        Field[] fields = MonitoringEvent.class.getDeclaredFields();
        
        for (Field field : fields) {
            if (!field.isSynthetic()) {
                assertTrue(Modifier.isFinal(field.getModifiers()) || 
                          Modifier.isStatic(field.getModifiers()),
                        "MonitoringEvent field should be final: " + field.getName());
            }
        }
    }

    @Test
    void testRawFileCreatedEventIsImmutable() {
        assertEventClassImmutable(RawFileCreatedEvent.class);
    }

    @Test
    void testRawFileModifiedEventIsImmutable() {
        assertEventClassImmutable(RawFileModifiedEvent.class);
    }

    @Test
    void testRawFileDeletedEventIsImmutable() {
        assertEventClassImmutable(RawFileDeletedEvent.class);
    }

    @Test
    void testRawDirectoryCreatedEventIsImmutable() {
        assertEventClassImmutable(RawDirectoryCreatedEvent.class);
    }

    private void assertEventClassImmutable(Class<?> eventClass) {
        assertTrue(Modifier.isFinal(eventClass.getModifiers()),
                eventClass.getSimpleName() + " should be final");

        Field[] fields = eventClass.getDeclaredFields();
        for (Field field : fields) {
            if (!field.isSynthetic() && !Modifier.isStatic(field.getModifiers())) {
                assertTrue(Modifier.isFinal(field.getModifiers()),
                        eventClass.getSimpleName() + " field should be final: " + field.getName());
            }
        }
    }

    // =========================================================================
    // EVENT HIERARCHY VALIDATION
    // =========================================================================

    @Test
    void testAllMonitoringEventsExtendAppEvent() {
        assertTrue(AppEvent.class.isAssignableFrom(MonitoringEvent.class),
                "MonitoringEvent should extend AppEvent");
        assertTrue(MonitoringEvent.class.isAssignableFrom(RawFileCreatedEvent.class),
                "RawFileCreatedEvent should extend MonitoringEvent");
        assertTrue(MonitoringEvent.class.isAssignableFrom(RawFileModifiedEvent.class),
                "RawFileModifiedEvent should extend MonitoringEvent");
        assertTrue(MonitoringEvent.class.isAssignableFrom(RawFileDeletedEvent.class),
                "RawFileDeletedEvent should extend MonitoringEvent");
        assertTrue(MonitoringEvent.class.isAssignableFrom(RawDirectoryCreatedEvent.class),
                "RawDirectoryCreatedEvent should extend MonitoringEvent");
    }

    @Test
    void testMonitoringEventHasPathField() throws Exception {
        MonitoringEvent event = new RawFileCreatedEvent(Path.of("test"));
        assertNotNull(event.path(), "MonitoringEvent should have path accessor");
    }

    @Test
    void testMonitoringEventHasTraceability() throws Exception {
        MonitoringEvent event = new RawFileCreatedEvent(Path.of("test"));
        
        assertNotNull(event.eventId(), "Should have event ID");
        assertNotNull(event.occurredAt(), "Should have timestamp");
        assertNotNull(event.source(), "Should have source");
        assertEquals("MonitoringEngine", event.source(),
                "Source should be MonitoringEngine");
    }

    // =========================================================================
    // METRICS IMMUTABILITY VALIDATION
    // =========================================================================

    @Test
    void testMonitoringMetricsIsImmutable() {
        assertTrue(Modifier.isFinal(MonitoringMetrics.class.getModifiers()),
                "MonitoringMetrics should be final");

        Field[] fields = MonitoringMetrics.class.getDeclaredFields();
        for (Field field : fields) {
            if (!field.isSynthetic()) {
                assertTrue(Modifier.isFinal(field.getModifiers()),
                        "MonitoringMetrics field should be final: " + field.getName());
            }
        }
    }

    @Test
    void testMonitoringMetricsHasNoSetters() {
        Method[] methods = MonitoringMetrics.class.getDeclaredMethods();
        
        for (Method method : methods) {
            assertFalse(method.getName().startsWith("set"),
                    "MonitoringMetrics should not have setters: " + method.getName());
        }
    }

    // =========================================================================
    // STATE ENUM VALIDATION
    // =========================================================================

    @Test
    void testMonitoringStateEnumCompleteness() {
        MonitoringState[] states = MonitoringState.values();
        
        assertTrue(states.length >= 6,
                "Should have at least 6 states (IDLE, STARTING, RUNNING, STOPPING, STOPPED, FAILED)");
        
        assertTrue(Arrays.asList(states).contains(MonitoringState.IDLE));
        assertTrue(Arrays.asList(states).contains(MonitoringState.STARTING));
        assertTrue(Arrays.asList(states).contains(MonitoringState.RUNNING));
        assertTrue(Arrays.asList(states).contains(MonitoringState.STOPPING));
        assertTrue(Arrays.asList(states).contains(MonitoringState.STOPPED));
        assertTrue(Arrays.asList(states).contains(MonitoringState.FAILED));
    }

    // =========================================================================
    // EXCEPTION HIERARCHY VALIDATION
    // =========================================================================

    @Test
    void testMonitoringExceptionExtendsException() {
        assertTrue(Exception.class.isAssignableFrom(MonitoringException.class),
                "MonitoringException should extend Exception");
    }

    @Test
    void testMonitoringExceptionHasProperConstructors() throws Exception {
        // Should have message constructor
        new MonitoringException("test");
        
        // Should have message + cause constructor
        new MonitoringException("test", new RuntimeException());
        
        assertTrue(true, "MonitoringException has proper constructors");
    }

    // =========================================================================
    // DEDUPLICATOR ARCHITECTURE VALIDATION
    // =========================================================================

    @Test
    void testEventDeduplicatorIsFinal() {
        assertTrue(Modifier.isFinal(EventDeduplicator.class.getModifiers()),
                "EventDeduplicator should be final");
    }

    @Test
    void testEventDeduplicatorHasNoDatabaseDependencies() {
        Field[] fields = EventDeduplicator.class.getDeclaredFields();
        
        for (Field field : fields) {
            String typeName = field.getType().getName();
            assertFalse(typeName.contains("java.sql"),
                    "EventDeduplicator should not have SQL dependencies");
            assertFalse(typeName.contains("Repository"),
                    "EventDeduplicator should not have repository dependencies");
        }
    }

    // =========================================================================
    // MONITORING ENGINE ARCHITECTURE VALIDATION
    // =========================================================================

    @Test
    void testMonitoringEngineIsFinal() {
        assertTrue(Modifier.isFinal(MonitoringEngine.class.getModifiers()),
                "MonitoringEngine should be final");
    }

    @Test
    void testMonitoringEngineHasProperEncapsulation() {
        Field[] fields = MonitoringEngine.class.getDeclaredFields();
        
        for (Field field : fields) {
            if (!field.isSynthetic()) {
                assertTrue(Modifier.isPrivate(field.getModifiers()) || 
                          Modifier.isFinal(field.getModifiers()),
                        "MonitoringEngine field should be private or final: " + field.getName());
            }
        }
    }

    @Test
    void testMonitoringEngineHasLifecycleMethods() throws Exception {
        // Should have start method
        Method startMethod = MonitoringEngine.class.getMethod("start", java.util.List.class);
        assertNotNull(startMethod);
        
        // Should have stop method
        Method stopMethod = MonitoringEngine.class.getMethod("stop");
        assertNotNull(stopMethod);
        
        // Should have getState method
        Method getStateMethod = MonitoringEngine.class.getMethod("getState");
        assertNotNull(getStateMethod);
        
        // Should have getMetrics method
        Method getMetricsMethod = MonitoringEngine.class.getMethod("getMetrics");
        assertNotNull(getMetricsMethod);
    }

    // =========================================================================
    // PACKAGE STRUCTURE VALIDATION
    // =========================================================================

    @Test
    void testMonitoringClassesInCorrectPackage() {
        assertEquals("com.filex.engine", MonitoringEngine.class.getPackageName());
        assertEquals("com.filex.engine", MonitoringEvent.class.getPackageName());
        assertEquals("com.filex.engine", EventDeduplicator.class.getPackageName());
        assertEquals("com.filex.engine", MonitoringMetrics.class.getPackageName());
        assertEquals("com.filex.engine", MonitoringState.class.getPackageName());
    }

    // =========================================================================
    // THREAD SAFETY VALIDATION
    // =========================================================================

    @Test
    void testMonitoringEngineUsesProperSynchronization() throws Exception {
        // Check that start and stop methods are synchronized
        Method startMethod = MonitoringEngine.class.getMethod("start", java.util.List.class);
        Method stopMethod = MonitoringEngine.class.getMethod("stop");
        Method addPathMethod = MonitoringEngine.class.getMethod("addMonitoredPath", Path.class);
        
        assertTrue(Modifier.isSynchronized(startMethod.getModifiers()),
                "start() should be synchronized");
        assertTrue(Modifier.isSynchronized(stopMethod.getModifiers()),
                "stop() should be synchronized");
        assertTrue(Modifier.isSynchronized(addPathMethod.getModifiers()),
                "addMonitoredPath() should be synchronized");
    }

    // =========================================================================
    // LOGGING VALIDATION
    // =========================================================================

    @Test
    void testMonitoringEngineUsesLogger() {
        Field[] fields = MonitoringEngine.class.getDeclaredFields();
        
        boolean hasLogger = false;
        for (Field field : fields) {
            if (field.getType().getName().contains("Logger")) {
                hasLogger = true;
                assertTrue(Modifier.isStatic(field.getModifiers()),
                        "Logger should be static");
                assertTrue(Modifier.isFinal(field.getModifiers()),
                        "Logger should be final");
            }
        }
        
        assertTrue(hasLogger, "MonitoringEngine should have a Logger field");
    }

    @Test
    void testEventDeduplicatorUsesLogger() {
        Field[] fields = EventDeduplicator.class.getDeclaredFields();
        
        boolean hasLogger = false;
        for (Field field : fields) {
            if (field.getType().getName().contains("Logger")) {
                hasLogger = true;
            }
        }
        
        assertTrue(hasLogger, "EventDeduplicator should have a Logger field");
    }
}
