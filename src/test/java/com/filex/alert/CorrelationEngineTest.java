package com.filex.alert;

import com.filex.detection.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CorrelationEngine.
 */
class CorrelationEngineTest {

    @TempDir
    Path tempDir;

    private CorrelationEngine correlationEngine;

    @BeforeEach
    void setUp() {
        correlationEngine = new CorrelationEngine(300_000); // 5 minute window
    }

    @Test
    void testNoCorrelationForFirstDetection() {
        MassDeletionDetectedEvent detection = new MassDeletionDetectedEvent(
                "MassDeletionRule",
                Severity.HIGH,
                Confidence.HIGH,
                "Mass deletion",
                List.of(tempDir.resolve("file1.txt")),
                60,
                10000
        );

        String correlatedIncident = correlationEngine.findCorrelatedIncident(detection);
        assertNull(correlatedIncident, "First detection should not correlate");
    }

    @Test
    void testCorrelationAfterRegistration() {
        MassDeletionDetectedEvent detection1 = new MassDeletionDetectedEvent(
                "MassDeletionRule",
                Severity.HIGH,
                Confidence.HIGH,
                "Mass deletion",
                List.of(tempDir.resolve("file1.txt")),
                60,
                10000
        );

        // Register incident
        String incidentId = "incident-123";
        correlationEngine.registerIncident(detection1, incidentId);

        // Second similar detection should correlate
        MassDeletionDetectedEvent detection2 = new MassDeletionDetectedEvent(
                "MassDeletionRule",
                Severity.HIGH,
                Confidence.HIGH,
                "Mass deletion",
                List.of(tempDir.resolve("file2.txt")),
                70,
                10000
        );

        String correlatedIncident = correlationEngine.findCorrelatedIncident(detection2);
        assertEquals(incidentId, correlatedIncident, "Should correlate with registered incident");
    }

    @Test
    void testDifferentRulesDoNotCorrelate() {
        MassDeletionDetectedEvent detection1 = new MassDeletionDetectedEvent(
                "MassDeletionRule",
                Severity.HIGH,
                Confidence.HIGH,
                "Mass deletion",
                List.of(tempDir.resolve("file1.txt")),
                60,
                10000
        );

        correlationEngine.registerIncident(detection1, "incident-123");

        // Different rule type
        HiddenFileDetectedEvent detection2 = new HiddenFileDetectedEvent(
                "HiddenFileCreationRule",
                Severity.LOW,
                Confidence.LOW,
                "Hidden file",
                List.of(tempDir.resolve(".hidden"))
        );

        String correlatedIncident = correlationEngine.findCorrelatedIncident(detection2);
        assertNull(correlatedIncident, "Different rules should not correlate");
    }

    @Test
    void testCorrelationExpiration() throws Exception {
        CorrelationEngine shortWindowEngine = new CorrelationEngine(100); // 100ms window

        MassDeletionDetectedEvent detection1 = new MassDeletionDetectedEvent(
                "MassDeletionRule",
                Severity.HIGH,
                Confidence.HIGH,
                "Mass deletion",
                List.of(tempDir.resolve("file1.txt")),
                60,
                10000
        );

        shortWindowEngine.registerIncident(detection1, "incident-123");

        // Wait for correlation to expire
        Thread.sleep(200);

        MassDeletionDetectedEvent detection2 = new MassDeletionDetectedEvent(
                "MassDeletionRule",
                Severity.HIGH,
                Confidence.HIGH,
                "Mass deletion",
                List.of(tempDir.resolve("file2.txt")),
                70,
                10000
        );

        String correlatedIncident = shortWindowEngine.findCorrelatedIncident(detection2);
        assertNull(correlatedIncident, "Expired correlation should not match");
    }

    @Test
    void testCleanupExpiredCorrelations() throws Exception {
        CorrelationEngine shortWindowEngine = new CorrelationEngine(100);

        // Register multiple incidents with different rules to create distinct correlation keys
        for (int i = 0; i < 10; i++) {
            MassDeletionDetectedEvent detection = new MassDeletionDetectedEvent(
                    "MassDeletionRule" + i,  // Different rule name for each
                    Severity.HIGH,
                    Confidence.HIGH,
                    "Mass deletion " + i,
                    List.of(tempDir.resolve("file" + i + ".txt")),
                    60,
                    10000
            );
            shortWindowEngine.registerIncident(detection, "incident-" + i);
        }

        assertEquals(10, shortWindowEngine.getActiveCorrelationCount());

        // Wait for expiration (100ms window + buffer for all registrations)
        Thread.sleep(250);

        int removed = shortWindowEngine.cleanupExpiredCorrelations();
        assertEquals(10, removed, "Should remove all expired correlations");
        assertEquals(0, shortWindowEngine.getActiveCorrelationCount());
    }

    @Test
    void testUpdateCorrelation() {
        MassDeletionDetectedEvent detection = new MassDeletionDetectedEvent(
                "MassDeletionRule",
                Severity.HIGH,
                Confidence.HIGH,
                "Mass deletion",
                List.of(tempDir.resolve("file1.txt")),
                60,
                10000
        );

        correlationEngine.registerIncident(detection, "incident-123");
        
        // Update should not throw
        correlationEngine.updateCorrelation(detection);
        
        // Should still correlate
        String correlatedIncident = correlationEngine.findCorrelatedIncident(detection);
        assertEquals("incident-123", correlatedIncident);
    }

    @Test
    void testClear() {
        MassDeletionDetectedEvent detection = new MassDeletionDetectedEvent(
                "MassDeletionRule",
                Severity.HIGH,
                Confidence.HIGH,
                "Mass deletion",
                List.of(tempDir.resolve("file1.txt")),
                60,
                10000
        );

        correlationEngine.registerIncident(detection, "incident-123");
        assertEquals(1, correlationEngine.getActiveCorrelationCount());

        correlationEngine.clear();
        assertEquals(0, correlationEngine.getActiveCorrelationCount());
    }
}
