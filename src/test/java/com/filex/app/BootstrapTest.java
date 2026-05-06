package com.filex.app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic smoke test for the bootstrap sequence.
 *
 * <p>Phase 1A: Validates that the bootstrap can execute without throwing
 * exceptions and produces a valid {@link AppContext}.
 *
 * <p>Future phases will add more granular unit tests for individual components.
 */
class BootstrapTest {

    @Test
    void testBootstrapInitializesSuccessfully() {
        // When: bootstrap is executed
        AppContext context = Bootstrap.initialize();

        // Then: context is non-null and fully initialized
        assertNotNull(context, "AppContext should not be null");
        assertNotNull(context.config(), "Config should not be null");
        assertNotNull(context.databaseManager(), "DatabaseManager should not be null");
        assertNotNull(context.eventBus(), "EventBus should not be null");
        assertNotNull(context.viewManager(), "ViewManager should not be null");

        // Verify database is connected
        assertTrue(context.databaseManager().isConnected(),
                "DatabaseManager should be connected after bootstrap");

        // Cleanup
        context.databaseManager().shutdown();
    }

    @Test
    void testBootstrapCreatesRequiredDirectories() {
        // When: bootstrap is executed
        AppContext context = Bootstrap.initialize();

        // Then: all required directories exist
        assertTrue(context.config().appHome().toFile().exists(),
                "App home directory should exist");
        assertTrue(context.config().logsDir().toFile().exists(),
                "Logs directory should exist");
        assertTrue(context.config().dataDir().toFile().exists(),
                "Data directory should exist");
        assertTrue(context.config().configDir().toFile().exists(),
                "Config directory should exist");

        // Cleanup
        context.databaseManager().shutdown();
    }
}
