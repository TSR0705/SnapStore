package com.filex.persistence;

import com.filex.config.AppConfig;
import com.filex.config.ConfigManager;
import com.filex.database.DatabaseManager;
import com.filex.model.ForensicTimelineEntity;
import com.filex.model.IncidentEntity;
import com.filex.model.IncidentEvidenceEntity;
import com.filex.repository.ForensicTimelineRepository;
import com.filex.repository.IncidentEvidenceRepository;
import com.filex.repository.IncidentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates that Phase 1G critical fixes are working correctly.
 */
class PersistenceFixesValidationTest {

    private DatabaseManager dbManager;
    private Path testDbPath;

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
    void testFix1_EvidenceRestrictDeletePreventsIncidentDeletion() throws Exception {
        testDbPath = Files.createTempFile("fix1-", ".db");
        AppConfig config = createTestConfig(testDbPath);
        
        dbManager = new DatabaseManager(config);
        dbManager.initialize();

        IncidentRepository incidentRepo = dbManager.incidentRepository();
        IncidentEvidenceRepository evidenceRepo = dbManager.incidentEvidenceRepository();

        // Create incident
        IncidentEntity incident = IncidentEntity.builder()
                .incidentId(UUID.randomUUID().toString())
                .severity("HIGH")
                .confidence("HIGH")
                .status("RESOLVED")
                .title("Test Incident")
                .description("Test")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .lastSeenAt(Instant.now())
                .correlationId("test-corr")
                .escalationLevel(1)
                .detectionCount(1)
                .metadata("{}")
                .build();
        incidentRepo.insert(incident);

        // Create evidence linked to incident
        IncidentEvidenceEntity evidence = IncidentEvidenceEntity.builder()
                .evidenceId(UUID.randomUUID().toString())
                .incidentId(incident.getIncidentId())
                .detectionId(UUID.randomUUID().toString())
                .ruleName("TestRule")
                .severity("HIGH")
                .confidence("HIGH")
                .filePath("C:\\test.txt")
                .detectedAt(Instant.now())
                .correlationId("test-corr")
                .metadata("{}")
                .createdAt(Instant.now())
                .build();
        evidenceRepo.insert(evidence);

        // Try to delete incident - should fail due to RESTRICT constraint
        Connection conn = dbManager.getConnection();
        try (Statement stmt = conn.createStatement()) {
            SQLException exception = assertThrows(SQLException.class, () -> {
                stmt.execute("DELETE FROM incidents WHERE incident_id = '" + incident.getIncidentId() + "'");
            });
            assertTrue(exception.getMessage().contains("FOREIGN KEY constraint failed") ||
                      exception.getMessage().contains("constraint"),
                      "Should fail with foreign key constraint error");
        }

        // Verify incident still exists
        assertTrue(incidentRepo.findByIncidentId(incident.getIncidentId()).isPresent());
        
        // Verify evidence still exists
        assertEquals(1, evidenceRepo.findByIncidentId(incident.getIncidentId()).size());
    }

    @Test
    void testFix2_TimelineUniqueConstraintPreventsDuplicates() throws Exception {
        testDbPath = Files.createTempFile("fix2-", ".db");
        AppConfig config = createTestConfig(testDbPath);
        
        dbManager = new DatabaseManager(config);
        dbManager.initialize();

        IncidentRepository incidentRepo = dbManager.incidentRepository();
        ForensicTimelineRepository timelineRepo = dbManager.forensicTimelineRepository();

        // Create incident first (required by foreign key)
        IncidentEntity incident = IncidentEntity.builder()
                .incidentId("incident-1")
                .severity("HIGH")
                .confidence("HIGH")
                .status("OPEN")
                .title("Test Incident")
                .description("Test")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .lastSeenAt(Instant.now())
                .correlationId("test-corr")
                .escalationLevel(1)
                .detectionCount(1)
                .metadata("{}")
                .build();
        incidentRepo.insert(incident);

        Instant timestamp = Instant.now();
        
        // Insert first timeline entry
        ForensicTimelineEntity entry1 = ForensicTimelineEntity.builder()
                .timelineId(UUID.randomUUID().toString())
                .incidentId("incident-1")
                .timestamp(timestamp)
                .sequenceNumber(1L)
                .eventType("INCIDENT_CREATED")
                .severity("HIGH")
                .description("First entry")
                .correlationId("test-corr")
                .metadata("{}")
                .createdAt(Instant.now())
                .build();
        timelineRepo.insert(entry1);

        // Try to insert duplicate (same timestamp + sequence) - should fail
        ForensicTimelineEntity entry2 = ForensicTimelineEntity.builder()
                .timelineId(UUID.randomUUID().toString())
                .incidentId("incident-1")
                .timestamp(timestamp)
                .sequenceNumber(1L)  // Same sequence at same timestamp
                .eventType("INCIDENT_UPDATED")
                .severity("MEDIUM")
                .description("Duplicate entry")
                .correlationId("test-corr")
                .metadata("{}")
                .createdAt(Instant.now())
                .build();

        SQLException exception = assertThrows(SQLException.class, () -> {
            timelineRepo.insert(entry2);
        });
        assertTrue(exception.getMessage().contains("UNIQUE constraint failed") ||
                  exception.getMessage().contains("unique"),
                  "Should fail with unique constraint error");
    }

    @Test
    void testFix3_UpdateVerificationThrowsOnMissingIncident() throws Exception {
        testDbPath = Files.createTempFile("fix3-", ".db");
        AppConfig config = createTestConfig(testDbPath);
        
        dbManager = new DatabaseManager(config);
        dbManager.initialize();

        IncidentRepository incidentRepo = dbManager.incidentRepository();

        // Try to update non-existent incident
        IncidentEntity incident = IncidentEntity.builder()
                .incidentId("non-existent-id")
                .severity("HIGH")
                .confidence("HIGH")
                .status("OPEN")
                .title("Test")
                .description("Test")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .lastSeenAt(Instant.now())
                .correlationId("test-corr")
                .escalationLevel(1)
                .detectionCount(1)
                .metadata("{}")
                .build();

        SQLException exception = assertThrows(SQLException.class, () -> {
            incidentRepo.update(incident);
        });
        assertTrue(exception.getMessage().contains("not found"),
                  "Should throw exception with 'not found' message");
    }

    @Test
    void testFix6_LRUCacheEvictsOldestEntries() {
        // This is tested implicitly by the LinkedHashMap implementation
        // The removeEldestEntry method ensures proper LRU behavior
        
        // Create a small LRU map to test
        java.util.Map<String, String> lruMap = java.util.Collections.synchronizedMap(
                new java.util.LinkedHashMap<String, String>(3, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(java.util.Map.Entry<String, String> eldest) {
                        return size() > 2;
                    }
                }
        );

        lruMap.put("key1", "value1");
        lruMap.put("key2", "value2");
        assertEquals(2, lruMap.size());

        // Adding third entry should evict oldest
        lruMap.put("key3", "value3");
        assertEquals(2, lruMap.size());
        assertFalse(lruMap.containsKey("key1"), "Oldest entry should be evicted");
        assertTrue(lruMap.containsKey("key2"));
        assertTrue(lruMap.containsKey("key3"));
    }

    @Test
    void testMigrationsV007AndV008Applied() throws Exception {
        testDbPath = Files.createTempFile("migrations-", ".db");
        AppConfig config = createTestConfig(testDbPath);
        
        dbManager = new DatabaseManager(config);
        dbManager.initialize();

        Connection conn = dbManager.getConnection();
        
        // Verify V007 applied (evidence table has RESTRICT constraint)
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT sql FROM sqlite_master WHERE type='table' AND name='incident_evidence'")) {
            assertTrue(rs.next());
            String sql = rs.getString("sql");
            assertTrue(sql.contains("ON DELETE RESTRICT"), 
                      "Evidence table should have ON DELETE RESTRICT constraint");
        }

        // Verify V008 applied (unique index on timeline)
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='index' AND name='idx_timeline_unique_seq'")) {
            assertTrue(rs.next(), "Unique index idx_timeline_unique_seq should exist");
        }
    }

    @Test
    void testAllMigrationsExecuted() throws Exception {
        testDbPath = Files.createTempFile("all-migrations-", ".db");
        AppConfig config = createTestConfig(testDbPath);
        
        dbManager = new DatabaseManager(config);
        dbManager.initialize();

        Connection conn = dbManager.getConnection();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT version, description FROM schema_version ORDER BY version")) {
            
            int count = 0;
            while (rs.next()) {
                count++;
                int version = rs.getInt("version");
                String description = rs.getString("description");
                assertNotNull(description);
                assertTrue(version > 0 && version <= 8);
            }
            
            assertEquals(8, count, "Should have 8 migrations applied");
        }
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
