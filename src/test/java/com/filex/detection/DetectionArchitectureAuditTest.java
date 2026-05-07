package com.filex.detection;

import com.filex.detection.rules.*;
import com.filex.event.AppEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Architecture and code quality audit for Phase 1E detection engine.
 * 
 * Validates:
 * - Clean architecture boundaries
 * - No SQL in detection engine
 * - Proper immutability
 * - Rule isolation
 * - Event hierarchy correctness
 */
class DetectionArchitectureAuditTest {

    // =========================================================================
    // ARCHITECTURE BOUNDARY VALIDATION
    // =========================================================================

    @Test
    void testDetectionEngineHasNoSQLDependencies() {
        Field[] fields = DetectionEngine.class.getDeclaredFields();
        
        for (Field field : fields) {
            String typeName = field.getType().getName();
            assertFalse(typeName.contains("java.sql"),
                    "DetectionEngine should not have SQL dependencies: " + field.getName());
            assertFalse(typeName.contains("Connection"),
                    "DetectionEngine should not have database connections: " + field.getName());
        }
    }

    @Test
    void testDetectionEngineHasNoRepositoryDependencies() {
        Field[] fields = DetectionEngine.class.getDeclaredFields();
        
        for (Field field : fields) {
            String typeName = field.getType().getName();
            assertFalse(typeName.contains("Repository"),
                    "DetectionEngine should not depend on repositories: " + field.getName());
        }
    }

    @Test
    void testDetectionEngineHasNoUIDependencies() {
        Field[] fields = DetectionEngine.class.getDeclaredFields();
        
        for (Field field : fields) {
            String typeName = field.getType().getName();
            assertFalse(typeName.contains("javafx"),
                    "DetectionEngine should not depend on JavaFX: " + field.getName());
            assertFalse(typeName.contains("ViewManager"),
                    "DetectionEngine should not depend on ViewManager: " + field.getName());
        }
    }

    @Test
    void testDetectionEngineHasNoMonitoringEngineDependency() {
        Field[] fields = DetectionEngine.class.getDeclaredFields();
        
        for (Field field : fields) {
            String typeName = field.getType().getName();
            assertFalse(typeName.contains("MonitoringEngine"),
                    "DetectionEngine should not directly depend on MonitoringEngine: " + field.getName());
        }
    }

    // =========================================================================
    // EVENT IMMUTABILITY VALIDATION
    // =========================================================================

    @Test
    void testDetectionEventIsImmutable() {
        assertTrue(Modifier.isAbstract(DetectionEvent.class.getModifiers()),
                "DetectionEvent should be abstract");

        Field[] fields = DetectionEvent.class.getDeclaredFields();
        for (Field field : fields) {
            if (!field.isSynthetic()) {
                assertTrue(Modifier.isFinal(field.getModifiers()),
                        "DetectionEvent field should be final: " + field.getName());
            }
        }
    }

    @Test
    void testMassDeletionDetectedEventIsImmutable() {
        assertEventClassImmutable(MassDeletionDetectedEvent.class);
    }

    @Test
    void testRapidModificationDetectedEventIsImmutable() {
        assertEventClassImmutable(RapidModificationDetectedEvent.class);
    }

    @Test
    void testSuspiciousRenameDetectedEventIsImmutable() {
        assertEventClassImmutable(SuspiciousRenameDetectedEvent.class);
    }

    @Test
    void testHiddenFileDetectedEventIsImmutable() {
        assertEventClassImmutable(HiddenFileDetectedEvent.class);
    }

    @Test
    void testSensitiveDirectoryAccessDetectedEventIsImmutable() {
        assertEventClassImmutable(SensitiveDirectoryAccessDetectedEvent.class);
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
    void testAllDetectionEventsExtendAppEvent() {
        assertTrue(AppEvent.class.isAssignableFrom(DetectionEvent.class),
                "DetectionEvent should extend AppEvent");
        assertTrue(DetectionEvent.class.isAssignableFrom(MassDeletionDetectedEvent.class),
                "MassDeletionDetectedEvent should extend DetectionEvent");
        assertTrue(DetectionEvent.class.isAssignableFrom(RapidModificationDetectedEvent.class),
                "RapidModificationDetectedEvent should extend DetectionEvent");
        assertTrue(DetectionEvent.class.isAssignableFrom(SuspiciousRenameDetectedEvent.class),
                "SuspiciousRenameDetectedEvent should extend DetectionEvent");
        assertTrue(DetectionEvent.class.isAssignableFrom(HiddenFileDetectedEvent.class),
                "HiddenFileDetectedEvent should extend DetectionEvent");
        assertTrue(DetectionEvent.class.isAssignableFrom(SensitiveDirectoryAccessDetectedEvent.class),
                "SensitiveDirectoryAccessDetectedEvent should extend DetectionEvent");
    }

    @Test
    void testDetectionEventHasRequiredFields() throws Exception {
        // Create a test event
        DetectionEvent event = new HiddenFileDetectedEvent(
                "TestRule",
                Severity.LOW,
                Confidence.LOW,
                "Test description",
                java.util.List.of()
        );

        assertNotNull(event.ruleName(), "Should have ruleName");
        assertNotNull(event.severity(), "Should have severity");
        assertNotNull(event.confidence(), "Should have confidence");
        assertNotNull(event.description(), "Should have description");
        assertNotNull(event.affectedPaths(), "Should have affectedPaths");
        assertNotNull(event.eventId(), "Should have eventId");
        assertNotNull(event.occurredAt(), "Should have timestamp");
        assertEquals("DetectionEngine", event.source(), "Source should be DetectionEngine");
    }

    // =========================================================================
    // RULE ISOLATION VALIDATION
    // =========================================================================

    @Test
    void testRulesAreStateless() {
        // Rules should not have mutable instance state
        assertRuleStateless(MassDeletionRule.class);
        assertRuleStateless(RapidModificationRule.class);
        assertRuleStateless(SuspiciousExtensionRenameRule.class);
        assertRuleStateless(HiddenFileCreationRule.class);
        assertRuleStateless(SensitiveDirectoryActivityRule.class);
    }

    private void assertRuleStateless(Class<?> ruleClass) {
        Field[] fields = ruleClass.getDeclaredFields();
        
        for (Field field : fields) {
            if (!field.isSynthetic() && !Modifier.isStatic(field.getModifiers())) {
                // Only volatile boolean for enabled flag is acceptable
                if (field.getType().equals(boolean.class) && Modifier.isVolatile(field.getModifiers())) {
                    continue; // Acceptable for enable/disable
                }
                
                // Logger is acceptable
                if (field.getType().getName().contains("Logger")) {
                    continue;
                }
                
                fail(ruleClass.getSimpleName() + " should not have mutable instance state: " + field.getName());
            }
        }
    }

    @Test
    void testRulesImplementDetectionRuleInterface() {
        assertTrue(DetectionRule.class.isAssignableFrom(MassDeletionRule.class));
        assertTrue(DetectionRule.class.isAssignableFrom(RapidModificationRule.class));
        assertTrue(DetectionRule.class.isAssignableFrom(SuspiciousExtensionRenameRule.class));
        assertTrue(DetectionRule.class.isAssignableFrom(HiddenFileCreationRule.class));
        assertTrue(DetectionRule.class.isAssignableFrom(SensitiveDirectoryActivityRule.class));
    }

    @Test
    void testRulesAreFinal() {
        assertTrue(Modifier.isFinal(MassDeletionRule.class.getModifiers()));
        assertTrue(Modifier.isFinal(RapidModificationRule.class.getModifiers()));
        assertTrue(Modifier.isFinal(SuspiciousExtensionRenameRule.class.getModifiers()));
        assertTrue(Modifier.isFinal(HiddenFileCreationRule.class.getModifiers()));
        assertTrue(Modifier.isFinal(SensitiveDirectoryActivityRule.class.getModifiers()));
    }

    // =========================================================================
    // DETECTION CONTEXT VALIDATION
    // =========================================================================

    @Test
    void testDetectionContextIsImmutable() {
        assertTrue(Modifier.isFinal(DetectionContext.class.getModifiers()),
                "DetectionContext should be final");

        Field[] fields = DetectionContext.class.getDeclaredFields();
        for (Field field : fields) {
            if (!field.isSynthetic()) {
                assertTrue(Modifier.isFinal(field.getModifiers()),
                        "DetectionContext field should be final: " + field.getName());
            }
        }
    }

    // =========================================================================
    // DETECTION RESULT VALIDATION
    // =========================================================================

    @Test
    void testDetectionResultIsImmutable() {
        assertTrue(Modifier.isFinal(DetectionResult.class.getModifiers()),
                "DetectionResult should be final");

        Field[] fields = DetectionResult.class.getDeclaredFields();
        for (Field field : fields) {
            if (!field.isSynthetic()) {
                assertTrue(Modifier.isFinal(field.getModifiers()),
                        "DetectionResult field should be final: " + field.getName());
            }
        }
    }

    // =========================================================================
    // METRICS IMMUTABILITY VALIDATION
    // =========================================================================

    @Test
    void testDetectionMetricsIsImmutable() {
        assertTrue(Modifier.isFinal(DetectionMetrics.class.getModifiers()),
                "DetectionMetrics should be final");

        Field[] fields = DetectionMetrics.class.getDeclaredFields();
        for (Field field : fields) {
            if (!field.isSynthetic()) {
                assertTrue(Modifier.isFinal(field.getModifiers()),
                        "DetectionMetrics field should be final: " + field.getName());
            }
        }
    }

    @Test
    void testDetectionMetricsHasNoSetters() {
        Method[] methods = DetectionMetrics.class.getDeclaredMethods();
        
        for (Method method : methods) {
            assertFalse(method.getName().startsWith("set"),
                    "DetectionMetrics should not have setters: " + method.getName());
        }
    }

    // =========================================================================
    // STATE ENUM VALIDATION
    // =========================================================================

    @Test
    void testDetectionStateEnumCompleteness() {
        DetectionState[] states = DetectionState.values();
        
        assertTrue(states.length >= 6,
                "Should have at least 6 states");
        
        assertTrue(Arrays.asList(states).contains(DetectionState.IDLE));
        assertTrue(Arrays.asList(states).contains(DetectionState.STARTING));
        assertTrue(Arrays.asList(states).contains(DetectionState.RUNNING));
        assertTrue(Arrays.asList(states).contains(DetectionState.STOPPING));
        assertTrue(Arrays.asList(states).contains(DetectionState.STOPPED));
        assertTrue(Arrays.asList(states).contains(DetectionState.FAILED));
    }

    @Test
    void testSeverityEnumCompleteness() {
        Severity[] severities = Severity.values();
        
        assertTrue(severities.length >= 4);
        assertTrue(Arrays.asList(severities).contains(Severity.LOW));
        assertTrue(Arrays.asList(severities).contains(Severity.MEDIUM));
        assertTrue(Arrays.asList(severities).contains(Severity.HIGH));
        assertTrue(Arrays.asList(severities).contains(Severity.CRITICAL));
    }

    @Test
    void testConfidenceEnumCompleteness() {
        Confidence[] confidences = Confidence.values();
        
        assertTrue(confidences.length >= 3);
        assertTrue(Arrays.asList(confidences).contains(Confidence.LOW));
        assertTrue(Arrays.asList(confidences).contains(Confidence.MEDIUM));
        assertTrue(Arrays.asList(confidences).contains(Confidence.HIGH));
    }

    // =========================================================================
    // EXCEPTION HIERARCHY VALIDATION
    // =========================================================================

    @Test
    void testDetectionExceptionExtendsException() {
        assertTrue(Exception.class.isAssignableFrom(DetectionException.class),
                "DetectionException should extend Exception");
    }

    // =========================================================================
    // DETECTION ENGINE ARCHITECTURE VALIDATION
    // =========================================================================

    @Test
    void testDetectionEngineIsFinal() {
        assertTrue(Modifier.isFinal(DetectionEngine.class.getModifiers()),
                "DetectionEngine should be final");
    }

    @Test
    void testDetectionEngineHasProperEncapsulation() {
        Field[] fields = DetectionEngine.class.getDeclaredFields();
        
        for (Field field : fields) {
            if (!field.isSynthetic()) {
                assertTrue(Modifier.isPrivate(field.getModifiers()) || 
                          Modifier.isFinal(field.getModifiers()),
                        "DetectionEngine field should be private or final: " + field.getName());
            }
        }
    }

    @Test
    void testDetectionEngineHasLifecycleMethods() throws Exception {
        Method startMethod = DetectionEngine.class.getMethod("start");
        assertNotNull(startMethod);
        
        Method stopMethod = DetectionEngine.class.getMethod("stop");
        assertNotNull(stopMethod);
        
        Method getStateMethod = DetectionEngine.class.getMethod("getState");
        assertNotNull(getStateMethod);
        
        Method getMetricsMethod = DetectionEngine.class.getMethod("getMetrics");
        assertNotNull(getMetricsMethod);
    }

    @Test
    void testDetectionEngineUsesProperSynchronization() throws Exception {
        Method startMethod = DetectionEngine.class.getMethod("start");
        Method stopMethod = DetectionEngine.class.getMethod("stop");
        
        assertTrue(Modifier.isSynchronized(startMethod.getModifiers()),
                "start() should be synchronized");
        assertTrue(Modifier.isSynchronized(stopMethod.getModifiers()),
                "stop() should be synchronized");
    }

    // =========================================================================
    // PACKAGE STRUCTURE VALIDATION
    // =========================================================================

    @Test
    void testDetectionClassesInCorrectPackage() {
        assertEquals("com.filex.detection", DetectionEngine.class.getPackageName());
        assertEquals("com.filex.detection", DetectionEvent.class.getPackageName());
        assertEquals("com.filex.detection", DetectionContext.class.getPackageName());
        assertEquals("com.filex.detection", DetectionMetrics.class.getPackageName());
        assertEquals("com.filex.detection", DetectionState.class.getPackageName());
        assertEquals("com.filex.detection.rules", MassDeletionRule.class.getPackageName());
    }

    // =========================================================================
    // LOGGING VALIDATION
    // =========================================================================

    @Test
    void testDetectionEngineUsesLogger() {
        Field[] fields = DetectionEngine.class.getDeclaredFields();
        
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
        
        assertTrue(hasLogger, "DetectionEngine should have a Logger field");
    }

    @Test
    void testRulesUseLogger() {
        assertRuleHasLogger(MassDeletionRule.class);
        assertRuleHasLogger(RapidModificationRule.class);
        assertRuleHasLogger(SuspiciousExtensionRenameRule.class);
        assertRuleHasLogger(HiddenFileCreationRule.class);
        assertRuleHasLogger(SensitiveDirectoryActivityRule.class);
    }

    private void assertRuleHasLogger(Class<?> ruleClass) {
        Field[] fields = ruleClass.getDeclaredFields();
        
        boolean hasLogger = false;
        for (Field field : fields) {
            if (field.getType().getName().contains("Logger")) {
                hasLogger = true;
            }
        }
        
        assertTrue(hasLogger, ruleClass.getSimpleName() + " should have a Logger field");
    }
}
