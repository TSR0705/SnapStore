package com.filex.database;

import static org.junit.jupiter.api.Assertions.*;

import com.filex.config.AppConfig;
import com.filex.config.ConfigManager;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link DatabaseManager}. */
class DatabaseManagerTest {

  private AppConfig config;
  private DatabaseManager dbManager;

  @BeforeEach
  void setUp() {
    config = ConfigManager.resolve();
    dbManager = new DatabaseManager(config);
  }

  @AfterEach
  void tearDown() {
    if (dbManager != null) {
      dbManager.shutdown();
    }
  }

  @Test
  void testInitializeCreatesConnection() {
    // When: database is initialized
    dbManager.initialize();

    // Then: connection is established
    assertTrue(dbManager.isConnected(), "Database should be connected");
    assertNotNull(dbManager.getConnection(), "Connection should not be null");
  }

  @Test
  void testInitializeCreatesSchemaVersionTable() throws Exception {
    // When: database is initialized
    dbManager.initialize();

    // Then: schema_version table exists
    Connection conn = dbManager.getConnection();
    try (Statement stmt = conn.createStatement()) {
      ResultSet rs =
          stmt.executeQuery(
              "SELECT name FROM sqlite_master WHERE type='table' AND name='schema_version';");
      assertTrue(rs.next(), "schema_version table should exist");
    }
  }

  @Test
  void testInitializeCreatesStartupLogTable() throws Exception {
    // When: database is initialized
    dbManager.initialize();

    // Then: app_startup_log table exists and has at least one record
    Connection conn = dbManager.getConnection();
    try (Statement stmt = conn.createStatement()) {
      ResultSet rs = stmt.executeQuery("SELECT COUNT(*) as cnt FROM app_startup_log;");
      assertTrue(rs.next(), "Query should return a result");
      int count = rs.getInt("cnt");
      assertTrue(count > 0, "app_startup_log should have at least one record");
    }
  }

  @Test
  void testShutdownClosesConnection() {
    // Given: an initialized database
    dbManager.initialize();
    assertTrue(dbManager.isConnected());

    // When: shutdown is called
    dbManager.shutdown();

    // Then: connection is closed
    assertFalse(dbManager.isConnected(), "Database should not be connected after shutdown");
  }

  @Test
  void testGetConnectionBeforeInitializeThrowsException() {
    // When/Then: calling getConnection before initialize throws
    assertThrows(
        IllegalStateException.class,
        () -> dbManager.getConnection(),
        "Should throw IllegalStateException when not initialized");
  }
}
