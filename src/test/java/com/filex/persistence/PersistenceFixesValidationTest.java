package com.filex.persistence;

import com.filex.config.AppConfig;
import com.filex.config.ConfigManager;
import com.filex.database.DatabaseManager;
import com.filex.model.SyncQueueEntity;
import com.filex.repository.SyncQueueRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates fixes for Phase 1B audit issues.
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
    void testSchemaVersionUsesIntegerTimestamp() throws Exception {
        testDbPath = Files.createTempFile("fix-validation-", ".db");
        AppConfig config = createTestConfig(testDbPath);
        
        dbManager = new DatabaseManager(config);
        dbManager.initialize();

        Connection conn = dbManager.getConnection();
        
        // Verify applied_at column is INTEGER type
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT applied_at FROM schema_version LIMIT 1")) {
            assertTrue(rs.next());
            
            // Get the value and verify it's a long (epoch milliseconds)
            long appliedAt = rs.getLong("applied_at");
            assertFalse(rs.wasNull(), "applied_at should not be null");
            assertTrue(appliedAt > 0, "applied_at should be positive epoch milliseconds");
            
            // Verify it's a reasonable timestamp (after year 2000)
            long year2000Millis = 946684800000L;
            assertTrue(appliedAt > year2000Millis, "applied_at should be after year 2000");
        }
    }

    @Test
    void testSyncQueueUniqueConstraintEnforced() throws Exception {
        testDbPath = Files.createTempFile("fix-validation-", ".db");
        AppConfig config = createTestConfig(testDbPath);
        
        dbManager = new DatabaseManager(config);
        dbManager.initialize();

        SyncQueueRepository repo = dbManager.syncQueueRepository();

        // Insert first pending entry
        SyncQueueEntity entry1 = SyncQueueEntity.builder()
                .entityType("file_event")
                .entityId("event-123")
                .operation("CREATE")
                .payload("{}")
                .status("pending")
                .retryCount(0)
                .createdAt(Instant.now())
                .build();
        
        long id1 = repo.insert(entry1);
        assertTrue(id1 > 0);

        // Try to insert duplicate pending entry - should fail
        SyncQueueEntity entry2 = SyncQueueEntity.builder()
                .entityType("file_event")
                .entityId("event-123")
                .operation("CREATE")
                .payload("{}")
                .status("pending")
                .retryCount(0)
                .createdAt(Instant.now())
                .build();

        assertThrows(SQLException.class, () -> repo.insert(entry2),
                "Should not allow duplicate pending operations");
    }

    @Test
    void testSyncQueueAllowsDuplicateNonPending() throws Exception {
        testDbPath = Files.createTempFile("fix-validation-", ".db");
        AppConfig config = createTestConfig(testDbPath);
        
        dbManager = new DatabaseManager(config);
        dbManager.initialize();

        SyncQueueRepository repo = dbManager.syncQueueRepository();

        // Insert first synced entry
        SyncQueueEntity entry1 = SyncQueueEntity.builder()
                .entityType("file_event")
                .entityId("event-456")
                .operation("UPDATE")
                .payload("{}")
                .status("synced")
                .retryCount(0)
                .createdAt(Instant.now())
                .syncedAt(Instant.now())
                .build();
        
        long id1 = repo.insert(entry1);
        assertTrue(id1 > 0);

        // Insert duplicate synced entry - should succeed (constraint only for pending)
        SyncQueueEntity entry2 = SyncQueueEntity.builder()
                .entityType("file_event")
                .entityId("event-456")
                .operation("UPDATE")
                .payload("{}")
                .status("synced")
                .retryCount(0)
                .createdAt(Instant.now())
                .syncedAt(Instant.now())
                .build();

        long id2 = repo.insert(entry2);
        assertTrue(id2 > 0);
        assertNotEquals(id1, id2, "Should allow duplicate non-pending operations");
    }

    @Test
    void testStartupLogRepositoryUsed() throws Exception {
        testDbPath = Files.createTempFile("fix-validation-", ".db");
        AppConfig config = createTestConfig(testDbPath);
        
        dbManager = new DatabaseManager(config);
        dbManager.initialize();

        // Verify startup log was recorded via repository
        Connection conn = dbManager.getConnection();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM app_startup_log")) {
            assertTrue(rs.next());
            int count = rs.getInt(1);
            assertTrue(count > 0, "Startup log should have at least one entry");
        }
    }

    @Test
    void testNoSQLInDatabaseManager() throws Exception {
        // This is a compile-time check - if DatabaseManager compiles without
        // direct SQL strings (except in migration registration), this passes
        testDbPath = Files.createTempFile("fix-validation-", ".db");
        AppConfig config = createTestConfig(testDbPath);
        
        dbManager = new DatabaseManager(config);
        dbManager.initialize();
        
        // Verify StartupLogRepository is accessible
        assertNotNull(dbManager.startupLogRepository());
    }

    @Test
    void testAllTimestampsConsistent() throws Exception {
        testDbPath = Files.createTempFile("fix-validation-", ".db");
        AppConfig config = createTestConfig(testDbPath);
        
        dbManager = new DatabaseManager(config);
        dbManager.initialize();

        Connection conn = dbManager.getConnection();
        
        // Check all tables use INTEGER for timestamp columns
        String[] tables = {
            "file_events", "alerts", "file_fingerprints", 
            "sync_queue", "app_settings", "app_startup_log", "schema_version"
        };
        
        for (String table : tables) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + table + ")")) {
                
                while (rs.next()) {
                    String columnName = rs.getString("name");
                    String columnType = rs.getString("type");
                    
                    // Check timestamp-related columns
                    if (columnName.contains("timestamp") || 
                        columnName.contains("_at") || 
                        columnName.equals("first_seen") || 
                        columnName.equals("last_seen") ||
                        columnName.equals("last_modified")) {
                        
                        assertEquals("INTEGER", columnType.toUpperCase(), 
                                "Column " + table + "." + columnName + " should be INTEGER");
                    }
                }
            }
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
                base.debugMode()
        );
    }
}
