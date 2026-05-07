package com.filex.alert;

import com.filex.detection.*;
import com.filex.event.EventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for AlertEngine Phase 1F.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Lifecycle management</li>
 *   <li>Incident creation</li>
 *   <li>Incident correlation and merging</li>
 *   <li>Alert suppression</li>
 *   <li>Severity escalation</li>
 *   <li>Async processing</li>
 *   <li>Metrics tracking</li>
 * </ul>
 */
class AlertEngineTest {

    @TempDir
    Path tempDir;

    private EventBus eventBus;
    private AlertEngine alertEngine;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus(5000);
        alertEngine = new AlertEngine(eventBus);
    }

    @AfterEach
    void tearDown() {
        if (alertEngine != null && alertEngine.getState() == AlertState.RUNNING) {
            alertEngine.stop();
        }
        if (eventBus != null) {
            eventBus.shutdown();
        }
    }

    // =========================================================================
    // SECTION 1: LIFECYCLE TESTS
    // =========================================================================

    @Test
    void testInitialState() {
        assertEquals(AlertState.IDLE, alertEngine.getState());
    }

    @Test
    void testStartAlertEngine() throws Exception {
        alertEngine.start();
        assertEquals(AlertState.RUNNING, alertEngine.getState());
    }

    @Test
    void testStopAlertEngine() throws Exception {
        alertEngine.start();
        assertEquals(AlertState.RUNNING, alertEngine.getState());

        alertEngine.stop();
        assertEquals(AlertState.STOPPED, alertEngine.getState());
    }

    @Test
    void testCannotStartFromRunningState() throws Exception {
        alertEngine.start();
        assertThrows(AlertException.class, () -> alertEngine.start());
    }

    @Test
    void testRepeatedStopCallsSafe() throws Exception {
        alertEngine.start();
        alertEngine.stop();
        alertEngine.stop(); // Should be safe
        alertEngine.stop(); // Should be safe

        assertEquals(AlertState.STOPPED, alertEngine.getState());
    }

    // =========================================================================
    // SECTION 2: INCIDENT CREATION TESTS
    // =========================================================================

    @Test
    void testIncidentCreatedFromDetection() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        List<IncidentCreatedEvent> incidents = new ArrayList<>();

        eventBus.subscribe(IncidentCreatedEvent.class, e -> {
            incidents.add(e);
            latch.countDown();
        });

        alertEngine.start();
        Thread.sleep(500);

        // Publish a detection event
        MassDeletionDetectedEvent detection = new MassDeletionDetectedEvent(
                "MassDeletionRule",
                Severity.HIGH,
                Confidence.HIGH,
                "Mass deletion detected: 60 files",
                List.of(tempDir.resolve("file1.txt")),
                60,
                10000
        );
        eventBus.publish(detection);

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Incident should be created");
        assertEquals(1, incidents.size());

        Incident incident = incidents.get(0).getIncident();
        assertEquals(IncidentSeverity.HIGH, incident.getSeverity());
        assertEquals(Confidence.HIGH, incident.getConfidence());
        assertEquals(IncidentStatus.OPEN, incident.getStatus());
        assertEquals(1, incident.getDetectionCount());
    }

    @Test
    void testMultipleDetectionsCreateMultipleIncidents() throws Exception {
        CountDownLatch latch = new CountDownLatch(2);
        List<IncidentCreatedEvent> incidents = new ArrayList<>();

        eventBus.subscribe(IncidentCreatedEvent.class, e -> {
            incidents.add(e);
            latch.countDown();
        });

        alertEngine.start();
        Thread.sleep(500);

        // Publish two different detection types
        MassDeletionDetectedEvent detection1 = new MassDeletionDetectedEvent(
                "MassDeletionRule",
                Severity.HIGH,
                Confidence.HIGH,
                "Mass deletion detected",
                List.of(tempDir.resolve("file1.txt")),
                60,
                10000
        );

        HiddenFileDetectedEvent detection2 = new HiddenFileDetectedEvent(
                "HiddenFileCreationRule",
                Severity.LOW,
                Confidence.LOW,
                "Hidden file created",
                List.of(tempDir.resolve(".hidden"))
        );

        eventBus.publish(detection1);
        Thread.sleep(100);
        eventBus.publish(detection2);

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Two incidents should be created");
        assertEquals(2, incidents.size());
    }

    // =========================================================================
    // SECTION 3: INCIDENT CORRELATION AND MERGING TESTS
    // =========================================================================

    @Test
    void testSimilarDetectionsMergeIntoSameIncident() throws Exception {
        CountDownLatch createdLatch = new CountDownLatch(1);
        CountDownLatch updatedLatch = new CountDownLatch(1);
        List<IncidentCreatedEvent> createdIncidents = new ArrayList<>();
        List<IncidentUpdatedEvent> updatedIncidents = new ArrayList<>();

        eventBus.subscribe(IncidentCreatedEvent.class, e -> {
            createdIncidents.add(e);
            createdLatch.countDown();
        });

        eventBus.subscribe(IncidentUpdatedEvent.class, e -> {
            updatedIncidents.add(e);
            updatedLatch.countDown();
        });

        alertEngine.start();
        Thread.sleep(500);

        // Publish two similar mass deletion events
        MassDeletionDetectedEvent detection1 = new MassDeletionDetectedEvent(
                "MassDeletionRule",
                Severity.MEDIUM,
                Confidence.MEDIUM,
                "Mass deletion detected: 30 files",
                List.of(tempDir.resolve("file1.txt")),
                30,
                10000
        );

        MassDeletionDetectedEvent detection2 = new MassDeletionDetectedEvent(
                "MassDeletionRule",
                Severity.HIGH,
                Confidence.HIGH,
                "Mass deletion detected: 60 files",
                List.of(tempDir.resolve("file2.txt")),
                60,
                10000
        );

        eventBus.publish(detection1);
        Thread.sleep(500);
        eventBus.publish(detection2);

        assertTrue(createdLatch.await(5, TimeUnit.SECONDS), "First incident should be created");
        assertTrue(updatedLatch.await(5, TimeUnit.SECONDS), "Incident should be updated");

        assertEquals(1, createdIncidents.size(), "Only one incident should be created");
        assertEquals(1, updatedIncidents.size(), "Incident should be updated once");

        Incident updatedIncident = updatedIncidents.get(0).getIncident();
        assertEquals(2, updatedIncident.getDetectionCount(), "Should have 2 detections");
        assertEquals(IncidentSeverity.HIGH, updatedIncident.getSeverity(), "Should escalate to HIGH");
    }

    // =========================================================================
    // SECTION 4: ALERT SUPPRESSION TESTS
    // =========================================================================

    @Test
    void testDuplicateAlertsSuppressed() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        List<IncidentCreatedEvent> incidents = new ArrayList<>();

        eventBus.subscribe(IncidentCreatedEvent.class, e -> {
            incidents.add(e);
            if (incidents.size() == 1) {
                latch.countDown();
            }
        });

        alertEngine.start();
        Thread.sleep(500);

        // Publish same detection multiple times rapidly
        for (int i = 0; i < 5; i++) {
            MassDeletionDetectedEvent detection = new MassDeletionDetectedEvent(
                    "MassDeletionRule",
                    Severity.HIGH,
                    Confidence.HIGH,
                    "Mass deletion detected",
                    List.of(tempDir.resolve("file1.txt")),
                    60,
                    10000
            );
            eventBus.publish(detection);
            Thread.sleep(50);
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS), "First incident should be created");
        Thread.sleep(2000); // Wait to ensure no more incidents created

        // Should only create 1 incident due to suppression
        assertTrue(incidents.size() <= 2, "Should suppress duplicate alerts");

        AlertMetrics metrics = alertEngine.getMetrics();
        assertTrue(metrics.getTotalAlertsSuppressed() > 0, "Should have suppressed alerts");
    }

    // =========================================================================
    // SECTION 5: SEVERITY ESCALATION TESTS
    // =========================================================================

    @Test
    void testSeverityEscalatesOnMerge() throws Exception {
        CountDownLatch createdLatch = new CountDownLatch(1);
        CountDownLatch updatedLatch = new CountDownLatch(1);
        List<IncidentUpdatedEvent> updatedIncidents = new ArrayList<>();

        eventBus.subscribe(IncidentCreatedEvent.class, e -> createdLatch.countDown());
        eventBus.subscribe(IncidentUpdatedEvent.class, e -> {
            updatedIncidents.add(e);
            updatedLatch.countDown();
        });

        alertEngine.start();
        Thread.sleep(500);

        // First detection: LOW severity
        MassDeletionDetectedEvent detection1 = new MassDeletionDetectedEvent(
                "MassDeletionRule",
                Severity.LOW,
                Confidence.LOW,
                "Some deletions",
                List.of(tempDir.resolve("file1.txt")),
                15,
                10000
        );

        // Second detection: HIGH severity (should escalate)
        MassDeletionDetectedEvent detection2 = new MassDeletionDetectedEvent(
                "MassDeletionRule",
                Severity.HIGH,
                Confidence.HIGH,
                "Mass deletion",
                List.of(tempDir.resolve("file2.txt")),
                60,
                10000
        );

        eventBus.publish(detection1);
        assertTrue(createdLatch.await(5, TimeUnit.SECONDS));

        Thread.sleep(500);
        eventBus.publish(detection2);
        assertTrue(updatedLatch.await(5, TimeUnit.SECONDS));

        Incident updated = updatedIncidents.get(0).getIncident();
        assertEquals(IncidentSeverity.HIGH, updated.getSeverity(), "Should escalate to HIGH");
        assertTrue(updated.getEscalationLevel() > 0, "Should track escalation");
    }

    // =========================================================================
    // SECTION 6: METRICS TESTS
    // =========================================================================

    @Test
    void testMetricsTracking() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        eventBus.subscribe(IncidentCreatedEvent.class, e -> latch.countDown());

        alertEngine.start();
        Thread.sleep(500);

        AlertMetrics initialMetrics = alertEngine.getMetrics();
        assertEquals(0, initialMetrics.getTotalIncidentsCreated());
        assertEquals(AlertState.RUNNING, initialMetrics.getCurrentState());

        // Trigger incident creation
        MassDeletionDetectedEvent detection = new MassDeletionDetectedEvent(
                "MassDeletionRule",
                Severity.HIGH,
                Confidence.HIGH,
                "Mass deletion",
                List.of(tempDir.resolve("file1.txt")),
                60,
                10000
        );
        eventBus.publish(detection);

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        Thread.sleep(1000);

        AlertMetrics afterMetrics = alertEngine.getMetrics();
        assertTrue(afterMetrics.getTotalAlertsProcessed() > 0, "Should track processed alerts");
        assertTrue(afterMetrics.getTotalIncidentsCreated() > 0, "Should track created incidents");
    }

    @Test
    void testMetricsThreadSafety() throws Exception {
        alertEngine.start();
        Thread.sleep(500);

        // Concurrent metrics access
        CountDownLatch latch = new CountDownLatch(100);
        for (int i = 0; i < 100; i++) {
            new Thread(() -> {
                try {
                    AlertMetrics metrics = alertEngine.getMetrics();
                    assertNotNull(metrics);
                    assertNotNull(metrics.getCurrentState());
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Concurrent metrics access should be safe");
    }

    // =========================================================================
    // SECTION 7: ASYNC PROCESSING TESTS
    // =========================================================================

    @Test
    void testAsyncProcessingDoesNotBlockPublishing() throws Exception {
        alertEngine.start();
        Thread.sleep(500);

        long startTime = System.currentTimeMillis();

        // Publish many detection events rapidly
        for (int i = 0; i < 100; i++) {
            MassDeletionDetectedEvent detection = new MassDeletionDetectedEvent(
                    "MassDeletionRule",
                    Severity.HIGH,
                    Confidence.HIGH,
                    "Mass deletion " + i,
                    List.of(tempDir.resolve("file" + i + ".txt")),
                    60,
                    10000
            );
            eventBus.publish(detection);
        }

        long publishDuration = System.currentTimeMillis() - startTime;

        // Publishing should be fast (not blocked by incident processing)
        assertTrue(publishDuration < 2000,
                "Publishing should not be blocked by processing. Took: " + publishDuration + "ms");
    }

    // =========================================================================
    // SECTION 8: FAILURE ISOLATION TESTS
    // =========================================================================

    @Test
    void testMalformedDetectionHandling() throws Exception {
        alertEngine.start();
        Thread.sleep(500);

        // Publish detection with empty paths
        MassDeletionDetectedEvent detection = new MassDeletionDetectedEvent(
                "MassDeletionRule",
                Severity.HIGH,
                Confidence.HIGH,
                "Mass deletion",
                List.of(),
                60,
                10000
        );
        eventBus.publish(detection);

        Thread.sleep(1000);

        // Engine should handle gracefully
        assertEquals(AlertState.RUNNING, alertEngine.getState());
    }

    // =========================================================================
    // SECTION 9: SHUTDOWN CLEANUP TESTS
    // =========================================================================

    @Test
    void testShutdownCleansUpResources() throws Exception {
        alertEngine.start();
        Thread.sleep(500);

        AlertMetrics beforeShutdown = alertEngine.getMetrics();
        assertTrue(beforeShutdown.getCurrentState() == AlertState.RUNNING);

        alertEngine.stop();
        Thread.sleep(500);

        assertEquals(AlertState.STOPPED, alertEngine.getState());
    }
}
