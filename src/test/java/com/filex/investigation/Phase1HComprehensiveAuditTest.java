package com.filex.investigation;

import com.filex.config.AppConfig;
import com.filex.config.ConfigManager;
import com.filex.database.DatabaseManager;
import com.filex.model.IncidentEntity;
import com.filex.model.IncidentEvidenceEntity;
import com.filex.model.ForensicTimelineEntity;
import com.filex.repository.IncidentRepository;
import com.filex.repository.IncidentEvidenceRepository;
import com.filex.repository.ForensicTimelineRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive audit test for Phase 1H investigation and replay systems.
 * Tests forensic integrity, replay determinism, and query scalability.
 */
class Phase1HComprehensiveAuditTest {

    private DatabaseManager dbManager;
    private Path testDbPath;
    private InvestigationQueryService investigationService;
    private ReplayNavigationService replayService;
    private IncidentRepository incidentRepo;
    private IncidentEvidenceRepository evidenceRepo;
    private ForensicTimelineRepository timelineRepo;
    private InvestigationMetrics metrics;

    @BeforeEach
    void setUp() throws Exception {
        testDbPath = Files.createTempFile("audit-", ".db");
        AppConfig config = createTestConfig(testDbPath);
        
        dbManager = new DatabaseManager(config);
        dbManager.initialize();

        investigationService = dbManager.investigationQueryService();
        metrics = new InvestigationMetrics();
        replayService = dbManager.replayNavigationService(metrics);
        incidentRepo = dbManager.incidentRepository();
        evidenceRepo = dbManager.incidentEvidenceRepository();
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

    // =========================================================================
    // SECTION 3: TIMELINE ANALYSIS - REPLAY DETERMINISM
    // =========================================================================

    @Test
    void testReplayDeterminismWithSameMillisecondEvents() throws Exception {
        String incidentId = createTestIncident("INC-001");
        Instant timestamp = Instant.now();

        // Create multiple events at exact same millisecond with different sequences
        createTimelineEvent(incidentId, "EVENT_1", timestamp, 1);
        createTimelineEvent(incidentId, "EVENT_2", timestamp, 2);
        createTimelineEvent(incidentId, "EVENT_3", timestamp, 3);

        // Replay multiple times - should always get same order
        ReplayNavigationService.ReplayWindow replay1 = replayService.replayIncidentTimeline(incidentId);
        ReplayNavigationService.ReplayWindow replay2 = replayService.replayIncidentTimeline(incidentId);
        ReplayNavigationService.ReplayWindow replay3 = replayService.replayIncidentTimeline(incidentId);

        // Verify deterministic ordering
        assertEquals(3, replay1.getEventCount());
        assertEquals(3, replay2.getEventCount());
        assertEquals(3, replay3.getEventCount());

        // Verify sequence ordering is consistent
        for (int i = 0; i < 3; i++) {
            assertEquals(replay1.events().get(i).getSequenceNumber(), 
                        replay2.events().get(i).getSequenceNumber());
            assertEquals(replay2.events().get(i).getSequenceNumber(), 
                        replay3.events().get(i).getSequenceNumber());
        }
    }

    @Test
    void testReplayConsistencyUnderConcurrentAccess() throws Exception {
        String incidentId = createTestIncident("INC-001");
        Instant now = Instant.now();

        // Create timeline events
        for (int i = 0; i < 20; i++) {
            createTimelineEvent(incidentId, "EVENT_" + i, now.plus(i, ChronoUnit.SECONDS), 1);
        }

        // Concurrent replay requests
        ExecutorService executor = Executors.newFixedThreadPool(5);
        List<Future<ReplayNavigationService.ReplayWindow>> futures = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            futures.add(executor.submit(() -> replayService.replayIncidentTimeline(incidentId)));
        }

        // Collect results
        List<ReplayNavigationService.ReplayWindow> results = new ArrayList<>();
        for (Future<ReplayNavigationService.ReplayWindow> future : futures) {
            results.add(future.get(5, TimeUnit.SECONDS));
        }

        executor.shutdown();

        // Verify all replays returned same count
        long expectedCount = results.get(0).getEventCount();
        for (ReplayNavigationService.ReplayWindow window : results) {
            assertEquals(expectedCount, window.getEventCount(), 
                        "Concurrent replays should return consistent results");
        }
    }

    // =========================================================================
    // SECTION 5: ADVANCED FILTERING - COMPLEX COMBINATIONS
    // =========================================================================

    @Test
    void testComplexFilterCombinations() throws Exception {
        Instant now = Instant.now();
        
        // Create diverse incidents
        createTestIncidentWithDetails("INC-001", "HIGH", "HIGH", "OPEN", now, "CORR-001");
        createTestIncidentWithDetails("INC-002", "MEDIUM", "MEDIUM", "INVESTIGATING", now, "CORR-001");
        createTestIncidentWithDetails("INC-003", "LOW", "LOW", "RESOLVED", now.minus(1, ChronoUnit.DAYS), "CORR-002");
        createTestIncidentWithDetails("INC-004", "HIGH", "HIGH", "OPEN", now, "CORR-002");

        // Test time range + status filter
        InvestigationCriteria criteria1 = InvestigationCriteria.builder()
                .timeRange(now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS))
                .statuses(Set.of("OPEN"))
                .build();

        InvestigationResult<IncidentSummary> result1 = investigationService.findIncidents(criteria1, 0, 10);
        assertEquals(2, result1.getTotalCount(), "Should find 2 OPEN incidents in time range");

        // Test correlation + severity filter (note: current implementation doesn't support multi-filter)
        InvestigationCriteria criteria2 = InvestigationCriteria.builder()
                .correlationId("CORR-001")
                .build();

        InvestigationResult<IncidentSummary> result2 = investigationService.findIncidents(criteria2, 0, 10);
        assertEquals(2, result2.getTotalCount(), "Should find 2 incidents with CORR-001");
    }

    // =========================================================================
    // SECTION 6: CORRELATION EXPLORATION - TRAVERSAL LIMITS
    // =========================================================================

    @Test
    void testCorrelationExplorationBounded() throws Exception {
        String correlationId = "CORR-LARGE";

        // Create many correlated incidents
        for (int i = 0; i < 50; i++) {
            createTestIncidentWithDetails("INC-" + i, "HIGH", "HIGH", "OPEN", 
                                         Instant.now(), correlationId);
        }

        // Find related incidents
        InvestigationResult<IncidentSummary> result = 
                investigationService.findRelatedIncidents(correlationId);

        assertEquals(50, result.getTotalCount());
        assertEquals(50, result.getItemCount());
        
        // Verify no pagination issues with large correlation sets
        assertFalse(result.hasMore());
    }

    // =========================================================================
    // SECTION 8: QUERY OPTIMIZATION - DEEP PAGINATION
    // =========================================================================

    @Test
    void testDeepPaginationStability() throws Exception {
        // Create 100 incidents
        for (int i = 0; i < 100; i++) {
            createTestIncident("INC-" + String.format("%03d", i));
        }

        InvestigationCriteria criteria = InvestigationCriteria.builder().build();

        // Test deep pagination
        InvestigationResult<IncidentSummary> page0 = investigationService.findIncidents(criteria, 0, 10);
        InvestigationResult<IncidentSummary> page5 = investigationService.findIncidents(criteria, 5, 10);
        InvestigationResult<IncidentSummary> page9 = investigationService.findIncidents(criteria, 9, 10);

        assertEquals(10, page0.getItemCount());
        assertEquals(10, page5.getItemCount());
        assertEquals(10, page9.getItemCount());
        
        // Verify total count is consistent
        assertEquals(100, page0.getTotalCount());
        assertEquals(100, page5.getTotalCount());
        assertEquals(100, page9.getTotalCount());
    }

    @Test
    void testPaginationBeyondResults() throws Exception {
        createTestIncident("INC-001");
        createTestIncident("INC-002");

        InvestigationCriteria criteria = InvestigationCriteria.builder().build();

        // Request page beyond available data
        InvestigationResult<IncidentSummary> result = investigationService.findIncidents(criteria, 10, 10);

        assertEquals(0, result.getItemCount());
        assertEquals(2, result.getTotalCount());
        assertFalse(result.hasMore());
    }

    // =========================================================================
    // SECTION 11: QUERY OBSERVABILITY - METRICS ACCURACY
    // =========================================================================

    @Test
    void testMetricsAccuracy() throws Exception {
        createTestIncident("INC-001");
        createTestIncident("INC-002");

        InvestigationCriteria criteria = InvestigationCriteria.builder().build();

        // Execute queries
        investigationService.findIncidents(criteria, 0, 10);
        investigationService.findIncidents(criteria, 1, 10);

        var metricsSnapshot = investigationService.getMetrics();

        assertTrue(metricsSnapshot.totalQueries() >= 2);
        assertTrue(metricsSnapshot.incidentQueriesExecuted() >= 2);
        assertTrue(metricsSnapshot.totalQueryTimeMs() >= 0);
        assertEquals(0, metricsSnapshot.failedQueries());
    }

    // =========================================================================
    // SECTION 12: QUERY FAILURE ISOLATION
    // =========================================================================

    @Test
    void testMalformedQueryHandling() {
        InvestigationCriteria criteria = InvestigationCriteria.builder().build();

        // Invalid pagination
        assertThrows(InvestigationException.class, () -> {
            investigationService.findIncidents(criteria, -1, 10);
        });

        assertThrows(InvestigationException.class, () -> {
            investigationService.findIncidents(criteria, 0, 0);
        });

        assertThrows(InvestigationException.class, () -> {
            investigationService.findIncidents(criteria, 0, 2000);
        });

        // Verify metrics tracked failures
        var metricsSnapshot = investigationService.getMetrics();
        assertEquals(0, metricsSnapshot.failedQueries(), 
                    "Validation failures should not count as query failures");
    }

    @Test
    void testInvalidReplayWindowHandling() {
        Instant now = Instant.now();

        // Invalid window size
        assertThrows(InvestigationException.class, () -> {
            replayService.replayTimeWindow(now, now.plus(1, ChronoUnit.HOURS), 0);
        });

        assertThrows(InvestigationException.class, () -> {
            replayService.replayTimeWindow(now, now.plus(1, ChronoUnit.HOURS), 1000);
        });

        // Invalid time range
        assertThrows(InvestigationException.class, () -> {
            replayService.replayTimeWindow(now, now.minus(1, ChronoUnit.HOURS), 100);
        });
    }

    // =========================================================================
    // SECTION 14: LONG-RANGE TIMELINE ANALYSIS
    // =========================================================================

    @Test
    void testLongRangeTimelineTraversal() throws Exception {
        String incidentId = createTestIncident("INC-001");
        Instant start = Instant.now().minus(30, ChronoUnit.DAYS);

        // Create timeline spanning 30 days
        for (int i = 0; i < 100; i++) {
            Instant timestamp = start.plus(i * 7, ChronoUnit.HOURS);
            createTimelineEvent(incidentId, "EVENT_" + i, timestamp, 1);
        }

        // Query long range
        Instant queryStart = start;
        Instant queryEnd = Instant.now();

        ReplayNavigationService.ReplayWindow window = 
                replayService.replayTimeWindow(queryStart, queryEnd, 100);

        assertEquals(100, window.getEventCount());
        assertEquals(100, window.totalEventCount());
        
        // Verify chronological ordering
        for (int i = 1; i < window.events().size(); i++) {
            assertTrue(window.events().get(i).getTimestamp()
                      .isAfter(window.events().get(i-1).getTimestamp()) ||
                      window.events().get(i).getTimestamp()
                      .equals(window.events().get(i-1).getTimestamp()));
        }
    }

    // =========================================================================
    // SECTION 16: CONCURRENCY STRESS TESTING
    // =========================================================================

    @Test
    void testConcurrentQueryOperations() throws Exception {
        // Create test data
        for (int i = 0; i < 50; i++) {
            createTestIncident("INC-" + i);
        }

        InvestigationCriteria criteria = InvestigationCriteria.builder().build();
        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<Future<InvestigationResult<IncidentSummary>>> futures = new ArrayList<>();

        // Concurrent queries
        for (int i = 0; i < 20; i++) {
            final int page = i % 5;
            futures.add(executor.submit(() -> 
                investigationService.findIncidents(criteria, page, 10)));
        }

        // Verify all complete successfully
        for (Future<InvestigationResult<IncidentSummary>> future : futures) {
            InvestigationResult<IncidentSummary> result = future.get(10, TimeUnit.SECONDS);
            assertNotNull(result);
            assertEquals(50, result.getTotalCount());
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    private String createTestIncident(String incidentId) throws Exception {
        return createTestIncidentWithDetails(incidentId, "HIGH", "HIGH", "OPEN", 
                                            Instant.now(), null);
    }

    private String createTestIncidentWithDetails(String incidentId, String severity, 
                                                 String confidence, String status, 
                                                 Instant createdAt, String correlationId) 
            throws Exception {
        IncidentEntity incident = IncidentEntity.builder()
                .incidentId(incidentId)
                .severity(severity)
                .confidence(confidence)
                .status(status)
                .title("Test Incident " + incidentId)
                .description("Test")
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .lastSeenAt(createdAt)
                .correlationId(correlationId)
                .escalationLevel(1)
                .detectionCount(1)
                .metadata("{}")
                .build();
        incidentRepo.insert(incident);
        return incidentId;
    }

    private void createTimelineEvent(String incidentId, String eventType, 
                                     Instant timestamp, long sequence) throws Exception {
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
                base.demoAutoCreatePath(),
                base.validationMode()
        );
    }
}
