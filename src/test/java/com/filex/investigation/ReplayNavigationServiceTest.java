package com.filex.investigation;

import com.filex.config.AppConfig;
import com.filex.config.ConfigManager;
import com.filex.database.DatabaseManager;
import com.filex.model.IncidentEntity;
import com.filex.model.ForensicTimelineEntity;
import com.filex.repository.IncidentRepository;
import com.filex.repository.ForensicTimelineRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ReplayNavigationService.
 */
class ReplayNavigationServiceTest {

    private DatabaseManager dbManager;
    private Path testDbPath;
    private ReplayNavigationService replayService;
    private IncidentRepository incidentRepo;
    private ForensicTimelineRepository timelineRepo;
    private InvestigationMetrics metrics;

    @BeforeEach
    void setUp() throws Exception {
        testDbPath = Files.createTempFile("replay-test-", ".db");
        AppConfig config = createTestConfig(testDbPath);
        
        dbManager = new DatabaseManager(config);
        dbManager.initialize();

        metrics = new InvestigationMetrics();
        replayService = dbManager.replayNavigationService(metrics);
        incidentRepo = dbManager.incidentRepository();
        timelineRepo = dbManager.forensicTimelineRepository();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (dbManager != null) {
            dbManager.shutdown();
        }
        if (testDbPath != null && Files.exists(testDbPath)) {
            Files.deleteIfExists(testDbPath);
        }
    }

    @Test
    void testReplayTimeWindow() throws Exception {
        Instant now = Instant.now();
        Instant start = now.minus(1, ChronoUnit.HOURS);
        Instant end = now.plus(1, ChronoUnit.HOURS);

        // Create timeline events
        String incidentId = createTestIncident("INC-001");
        createTimelineEvent(incidentId, "INCIDENT_CREATED", now.minus(30, ChronoUnit.MINUTES), 1);
        createTimelineEvent(incidentId, "INCIDENT_UPDATED", now, 1);
        createTimelineEvent(incidentId, "INCIDENT_RESOLVED", now.plus(30, ChronoUnit.MINUTES), 1);

        ReplayNavigationService.ReplayWindow window = replayService.replayTimeWindow(start, end, 100);

        assertNotNull(window);
        assertEquals(3, window.getEventCount());
        assertEquals(3, window.totalEventCount());
        assertFalse(window.hasMore());
        assertEquals(start, window.windowStart());
        assertEquals(end, window.windowEnd());
    }

    @Test
    void testReplayTimeWindowWithPagination() throws Exception {
        Instant now = Instant.now();
        Instant start = now.minus(1, ChronoUnit.HOURS);
        Instant end = now.plus(1, ChronoUnit.HOURS);

        String incidentId = createTestIncident("INC-001");
        
        // Create 10 timeline events
        for (int i = 0; i < 10; i++) {
            createTimelineEvent(incidentId, "EVENT_" + i, now.plus(i, ChronoUnit.MINUTES), i + 1);
        }

        // Request window of size 5
        ReplayNavigationService.ReplayWindow window = replayService.replayTimeWindow(start, end, 5);

        assertEquals(5, window.getEventCount());
        assertEquals(10, window.totalEventCount());
        assertTrue(window.hasMore());
    }

    @Test
    void testReplayIncidentTimeline() throws Exception {
        String incidentId = createTestIncident("INC-001");
        Instant now = Instant.now();

        // Create timeline events for incident
        createTimelineEvent(incidentId, "INCIDENT_CREATED", now, 1);
        createTimelineEvent(incidentId, "EVIDENCE_ADDED", now.plus(1, ChronoUnit.MINUTES), 1);
        createTimelineEvent(incidentId, "INCIDENT_UPDATED", now.plus(2, ChronoUnit.MINUTES), 1);

        ReplayNavigationService.ReplayWindow window = replayService.replayIncidentTimeline(incidentId);

        assertNotNull(window);
        assertEquals(3, window.getEventCount());
        assertFalse(window.hasMore());
        
        // Verify chronological ordering
        assertEquals("INCIDENT_CREATED", window.events().get(0).getEventType());
        assertEquals("EVIDENCE_ADDED", window.events().get(1).getEventType());
        assertEquals("INCIDENT_UPDATED", window.events().get(2).getEventType());
    }

    @Test
    void testReplayCorrelatedEvents() throws Exception {
        String correlationId = "CORR-001";
        Instant now = Instant.now();

        String incident1 = createTestIncident("INC-001");
        String incident2 = createTestIncident("INC-002");

        // Create correlated timeline events
        createCorrelatedTimelineEvent(incident1, "INCIDENT_CREATED", now, 1, correlationId);
        createCorrelatedTimelineEvent(incident2, "INCIDENT_CREATED", now.plus(1, ChronoUnit.MINUTES), 1, correlationId);
        createCorrelatedTimelineEvent(incident1, "INCIDENT_UPDATED", now.plus(2, ChronoUnit.MINUTES), 1, correlationId);

        ReplayNavigationService.ReplayWindow window = replayService.replayCorrelatedEvents(correlationId);

        assertNotNull(window);
        assertEquals(3, window.getEventCount());
        assertFalse(window.hasMore());
    }

    @Test
    void testFindNextCheckpoint() throws Exception {
        Instant now = Instant.now();
        Instant checkpoint = now.minus(30, ChronoUnit.MINUTES);

        String incidentId = createTestIncident("INC-001");

        // Create events before and after checkpoint
        createTimelineEvent(incidentId, "EVENT_BEFORE", checkpoint.minus(10, ChronoUnit.MINUTES), 1);
        createTimelineEvent(incidentId, "EVENT_AFTER_1", checkpoint.plus(10, ChronoUnit.MINUTES), 1);
        createTimelineEvent(incidentId, "EVENT_AFTER_2", checkpoint.plus(20, ChronoUnit.MINUTES), 1);

        ReplayNavigationService.ReplayWindow window = replayService.findNextCheckpoint(checkpoint, 100);

        assertNotNull(window);
        assertEquals(2, window.getEventCount());
        
        // Verify only events after checkpoint are included
        assertTrue(window.events().stream()
                .allMatch(e -> e.getTimestamp().isAfter(checkpoint)));
    }

    @Test
    void testDeterministicOrdering() throws Exception {
        Instant now = Instant.now();
        String incidentId = createTestIncident("INC-001");

        // Create events with same timestamp but different sequence numbers
        createTimelineEvent(incidentId, "EVENT_1", now, 1);
        createTimelineEvent(incidentId, "EVENT_2", now, 2);
        createTimelineEvent(incidentId, "EVENT_3", now, 3);

        ReplayNavigationService.ReplayWindow window = replayService.replayIncidentTimeline(incidentId);

        assertEquals(3, window.getEventCount());
        
        // Verify sequence ordering
        assertEquals(1, window.events().get(0).getSequenceNumber());
        assertEquals(2, window.events().get(1).getSequenceNumber());
        assertEquals(3, window.events().get(2).getSequenceNumber());
    }

    @Test
    void testEmptyReplayWindow() throws Exception {
        Instant now = Instant.now();
        Instant start = now.minus(1, ChronoUnit.HOURS);
        Instant end = now.minus(30, ChronoUnit.MINUTES);

        ReplayNavigationService.ReplayWindow window = replayService.replayTimeWindow(start, end, 100);

        assertNotNull(window);
        assertTrue(window.isEmpty());
        assertEquals(0, window.getEventCount());
        assertFalse(window.hasMore());
    }

    @Test
    void testInvalidWindowSizeThrowsException() {
        Instant now = Instant.now();

        assertThrows(InvestigationException.class, () -> {
            replayService.replayTimeWindow(now, now.plus(1, ChronoUnit.HOURS), 0);
        });

        assertThrows(InvestigationException.class, () -> {
            replayService.replayTimeWindow(now, now.plus(1, ChronoUnit.HOURS), 1000);
        });
    }

    @Test
    void testInvalidTimeRangeThrowsException() {
        Instant now = Instant.now();
        Instant past = now.minus(1, ChronoUnit.HOURS);

        assertThrows(InvestigationException.class, () -> {
            replayService.replayTimeWindow(now, past, 100);
        });
    }

    @Test
    void testMetricsTracking() throws Exception {
        String incidentId = createTestIncident("INC-001");
        createTimelineEvent(incidentId, "INCIDENT_CREATED", Instant.now(), 1);

        replayService.replayIncidentTimeline(incidentId);

        var metricsSnapshot = metrics.snapshot();
        assertTrue(metricsSnapshot.replayNavigations() > 0);
        assertTrue(metricsSnapshot.totalQueries() > 0);
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    private String createTestIncident(String incidentId) throws Exception {
        IncidentEntity incident = IncidentEntity.builder()
                .incidentId(incidentId)
                .severity("HIGH")
                .confidence("HIGH")
                .status("OPEN")
                .title("Test Incident")
                .description("Test")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .lastSeenAt(Instant.now())
                .correlationId(null)
                .escalationLevel(1)
                .detectionCount(1)
                .metadata("{}")
                .build();
        incidentRepo.insert(incident);
        return incidentId;
    }

    private void createTimelineEvent(String incidentId, String eventType, Instant timestamp, long sequence) 
            throws Exception {
        ForensicTimelineEntity timeline = ForensicTimelineEntity.builder()
                .timelineId(UUID.randomUUID().toString())
                .timestamp(timestamp)
                .sequenceNumber(sequence)
                .eventType(eventType)
                .incidentId(incidentId)
                .evidenceId(null)
                .detectionId(null)
                .severity("HIGH")
                .description("Test event")
                .correlationId(null)
                .metadata("{}")
                .createdAt(Instant.now())
                .build();
        timelineRepo.insert(timeline);
    }

    private void createCorrelatedTimelineEvent(String incidentId, String eventType, Instant timestamp, 
                                               long sequence, String correlationId) throws Exception {
        ForensicTimelineEntity timeline = ForensicTimelineEntity.builder()
                .timelineId(UUID.randomUUID().toString())
                .timestamp(timestamp)
                .sequenceNumber(sequence)
                .eventType(eventType)
                .incidentId(incidentId)
                .evidenceId(null)
                .detectionId(null)
                .severity("HIGH")
                .description("Test event")
                .correlationId(correlationId)
                .metadata("{}")
                .createdAt(Instant.now())
                .build();
        timelineRepo.insert(timeline);
    }

    private AppConfig createTestConfig(Path dbPath) {
        AppConfig base = ConfigManager.resolve();
        return new AppConfig(
                base.appHome(),
                base.logsDir(),
                base.dataDir(),
                base.configDir(),
                dbPath,
                base.appName(),
                base.appVersion(),
                base.debugMode(),
                base.demoMode(),
                base.demoMonitorPath(),
                base.demoAutoCreatePath()
        );
    }
}
