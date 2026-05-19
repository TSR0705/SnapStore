package com.filex.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.filex.config.AppConfig;
import com.filex.config.ConfigManager;
import com.filex.database.DatabaseManager;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for the migration system. */
class MigrationManagerTest {

  private AppConfig config;
  private DatabaseManager dbManager;
  private Connection connection;

  @BeforeEach
  void setUp() {
    config = ConfigManager.resolve();
    dbManager = new DatabaseManager(config);
    dbManager.initialize();
    connection = dbManager.getConnection();
  }

  @AfterEach
  void tearDown() {
    if (dbManager != null) {
      dbManager.shutdown();
    }
  }

  @Test
  void testMigrationsExecuteSuccessfully() throws Exception {
    // Migrations are run during dbManager.initialize()
    // Verify schema_version table exists and has entries

    try (Statement stmt = connection.createStatement()) {
      ResultSet rs = stmt.executeQuery("SELECT COUNT(*) as cnt FROM schema_version");
      assertTrue(rs.next());
      int count = rs.getInt("cnt");
      assertTrue(count >= 2, "Should have at least 2 migrations applied");
    }
  }

  @Test
  void testFileEventsTableExists() throws Exception {
    try (Statement stmt = connection.createStatement()) {
      ResultSet rs =
          stmt.executeQuery(
              "SELECT name FROM sqlite_master WHERE type='table' AND name='file_events'");
      assertTrue(rs.next(), "file_events table should exist");
    }
  }

  @Test
  void testAlertsTableExists() throws Exception {
    try (Statement stmt = connection.createStatement()) {
      ResultSet rs =
          stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='alerts'");
      assertTrue(rs.next(), "alerts table should exist");
    }
  }

  @Test
  void testIndexesCreated() throws Exception {
    try (Statement stmt = connection.createStatement()) {
      ResultSet rs =
          stmt.executeQuery(
              "SELECT COUNT(*) as cnt FROM sqlite_master WHERE type='index' AND name LIKE 'idx_%'");
      assertTrue(rs.next());
      int count = rs.getInt("cnt");
      assertTrue(count > 0, "Should have indexes created");
    }
  }
}
