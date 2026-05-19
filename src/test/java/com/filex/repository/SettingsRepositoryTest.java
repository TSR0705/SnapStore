package com.filex.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.filex.config.AppConfig;
import com.filex.config.ConfigManager;
import com.filex.database.DatabaseManager;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for SettingsRepository. */
class SettingsRepositoryTest {

  private DatabaseManager dbManager;
  private SettingsRepository repository;

  @BeforeEach
  void setUp() {
    AppConfig config = ConfigManager.resolve();
    dbManager = new DatabaseManager(config);
    dbManager.initialize();
    repository = dbManager.settingsRepository();
  }

  @AfterEach
  void tearDown() {
    if (dbManager != null) {
      dbManager.shutdown();
    }
  }

  @Test
  void testSaveAndRetrieveStringSetting() throws Exception {
    // When
    repository.saveString("theme", "dark", "UI theme preference");

    // Then
    Optional<String> value = repository.getString("theme");
    assertTrue(value.isPresent());
    assertEquals("dark", value.get());
  }

  @Test
  void testSaveAndRetrieveIntegerSetting() throws Exception {
    // When
    repository.saveInt("retention_days", 30, "Data retention period");

    // Then
    Optional<Integer> value = repository.getInt("retention_days");
    assertTrue(value.isPresent());
    assertEquals(30, value.get());
  }

  @Test
  void testSaveAndRetrieveBooleanSetting() throws Exception {
    // When
    repository.saveBoolean("debug_mode", true, "Enable debug logging");

    // Then
    Optional<Boolean> value = repository.getBoolean("debug_mode");
    assertTrue(value.isPresent());
    assertTrue(value.get());
  }

  @Test
  void testUpdateExistingSetting() throws Exception {
    // Given
    repository.saveInt("max_events", 1000, "Maximum events to store");

    // When
    repository.saveInt("max_events", 2000, "Maximum events to store");

    // Then
    Optional<Integer> value = repository.getInt("max_events");
    assertTrue(value.isPresent());
    assertEquals(2000, value.get());
  }

  @Test
  void testDeleteSetting() throws Exception {
    // Given
    repository.saveString("temp_setting", "value", "Temporary");

    // When
    repository.delete("temp_setting");

    // Then
    Optional<String> value = repository.getString("temp_setting");
    assertFalse(value.isPresent());
  }
}
