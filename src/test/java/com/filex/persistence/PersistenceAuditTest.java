package com.filex.persistence;

import com.filex.config.AppConfig;
import com.filex.config.ConfigManager;
import com.filex.database.DatabaseManager;
import com.filex.model.FileEventEntity;
import com.filex.repository.FileEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive audit tests for Phase 1B persistence layer.
 * Tests failure scenarios, transaction safety, and schema validation.
 */
class PersistenceAuditTest {

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
    void testSchemaVersionTableExists() throws Exception {
        testDbPath = Files.createTempFile("audit-", ".db");
        AppConfig config = createTestConfig(testDbPath);
        
        dbManager = new DatabaseManager(config);
        dbManager.initialize();

        Connection conn = dbManager.getConnection();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='schema_version'")) {
            assertTrue(rs.next(), "schema_version table should exist");
        }
    }

    @Test
    void testAllTablesCreated() throws Exception {
        testDbPath = Files.createTempFile("audit-", ".db");
        AppConfig config = createTestConfig(testDbPath);
        
        dbManager = new DatabaseManager(config);
        dbManager.initialize();

        String[] expectedTables = {
            "file_events", "alerts", "file_fingerprints", 
            "sync_queue", "app_settings", "app_startup_log", "schema_version"
        };

        Connection conn = dbManager.getConnection();
        for (String table : expectedTables) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + table + "'")) {
                assertTrue(rs.next(), "Table " + table + " should exist");
            }
        }
    }

    @Test
    void testIndexesCreated() throws Exception {
        testDbPath = Files.createTempFile("audit-", ".db");
        AppConfig config = createTestConfig(testDbPath);
        
        dbManager = new DatabaseManager(config);
        dbManager.initialize();

        Connection conn = dbManager.getConnection();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='index'")) {
            int indexCount = 0;
            while (rs.next()) {
                indexCount++;
            }
            assertTrue(indexCount > 0, "Indexes should be created");
        }
    }

    @Test
    void testForeignKeyConstraintsEnabled() throws Exception {
        testDbPath = Files.createTempFile("audit-", ".db");
        AppConfig config = createTestConfig(testDbPath);
        
        dbManager = new DatabaseManager(config);
        dbManager.initialize();

        Connection conn = dbManager.getConnection();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA foreign_keys")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1), "Foreign keys should be enabled");
        }
    }

    @Test
    void testWALModeEnabled() throws Exception {
        testDbPath = Files.createTempFile("audit-", ".db");
        AppConfig config = createTestConfig(testDbPath);
        
        dbManager = new DatabaseManager(config);
        dbManager.initialize();

        Connection conn = dbManager.getConnection();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA journal_mode")) {
            assertTrue(rs.next());
            assertEquals("wal", rs.getString(1).toLowerCase(), "WAL mode should be enabled");
        }
    }

    @Test
    void testTransactionRollback() throws Exception {
        testDbPath = Files.createTempFile("audit-", ".db");
        AppConfig config = createTestConfig(testDbPath);
        
        dbManager = new DatabaseManager(config);
        dbManager.initialize();

        FileEventRepository repo = dbManager.fileEventRepository();
        TransactionTemplate tx = dbManager.transactionTemplate();

        // Insert event in transaction that will be rolled back
        try {
            tx.execute(conn -> {
                try {
                    FileEventEntity event = FileEventEntity.builder()
                            .eventId(UUID.randomUUID().toString())
                            .timestamp(Instant.now())
                            .eventType("TEST")
                            .filePath("C:\\test.txt")
                            .suspicious(false)
                            .build();
                    repo.insert(event);
                    throw new RuntimeException("Simulated failure");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            fail("Should have thrown exception");
        } catch (Exception e) {
            // Expected
        }

        // Verify no events were persisted
        assertEquals(0, repo.count(), "Transaction should have been rolled back");
    }

    @Test
    void testRepeatedStartupSafe() throws Exception {
        testDbPath = Files.createTempFile("audit-", ".db");
        AppConfig config = createTestConfig(testDbPath);
        
        // First startup
        dbManager = new DatabaseManager(config);
        dbManager.initialize();
        dbManager.shutdown();

        // Second startup - should not fail
        dbManager = new DatabaseManager(config);
        dbManager.initialize();
        assertTrue(dbManager.isConnected());
    }

    @Test
    void testMigrationsExecuteOnlyOnce() throws Exception {
        testDbPath = Files.createTempFile("audit-", ".db");
        AppConfig config = createTestConfig(testDbPath);
        
        dbManager = new DatabaseManager(config);
        dbManager.initialize();

        Connection conn = dbManager.getConnection();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM schema_version")) {
            assertTrue(rs.next());
            int migrationCount = rs.getInt(1);
            assertEquals(4, migrationCount, "Should have exactly 4 migrations");
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
