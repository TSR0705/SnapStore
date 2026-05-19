package com.filex.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ConfigManager}.
 */
class ConfigManagerTest {

    @Test
    void testResolveCreatesValidConfig() {
        // When: configuration is resolved
        AppConfig config = ConfigManager.resolve();

        // Then: all required fields are populated
        assertNotNull(config.appHome(), "appHome should not be null");
        assertNotNull(config.logsDir(), "logsDir should not be null");
        assertNotNull(config.dataDir(), "dataDir should not be null");
        assertNotNull(config.configDir(), "configDir should not be null");
        assertNotNull(config.databaseFile(), "databaseFile should not be null");
        assertNotNull(config.appName(), "appName should not be null");
        assertNotNull(config.appVersion(), "appVersion should not be null");

        // Verify app name and version constants
        assertEquals(AppConfig.APP_NAME, config.appName());
        assertEquals(AppConfig.APP_VERSION, config.appVersion());

        // Verify database file name
        assertTrue(config.databaseFile().toString().endsWith(AppConfig.DB_FILENAME),
                "Database file should end with " + AppConfig.DB_FILENAME);
    }

    @Test
    void testResolveCreatesDirectories() {
        // When: configuration is resolved
        AppConfig config = ConfigManager.resolve();

        // Then: all directories exist on the filesystem
        assertTrue(config.appHome().toFile().exists(),
                "appHome directory should exist");
        assertTrue(config.logsDir().toFile().exists(),
                "logsDir directory should exist");
        assertTrue(config.dataDir().toFile().exists(),
                "dataDir directory should exist");
        assertTrue(config.configDir().toFile().exists(),
                "configDir directory should exist");
    }

    @Test
    void testConfigSummary() {
        // Given: a resolved config
        AppConfig config = ConfigManager.resolve();

        // When: summary is generated
        String summary = config.summary();

        // Then: summary contains key information
        assertNotNull(summary);
        assertTrue(summary.contains(config.appName()));
        assertTrue(summary.contains(config.appVersion()));
    }

}
