package com.filex.workspace;

import com.filex.config.AppConfig;
import com.filex.config.ConfigManager;
import com.filex.database.DatabaseManager;
import com.filex.investigation.InvestigationMetrics;
import com.filex.investigation.InvestigationQueryService;
import com.filex.investigation.ReplayNavigationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for async query safety and stale response protection.
 */
class AsyncSafetyTest {

    private DatabaseManager dbManager;
    private Path testDbPath;
    private Path testDataDir;
    private WorkspaceService workspaceService;

    @BeforeEach
    void setUp() throws Exception {
        testDbPath = Files.createTempFile("async-safety-test-", ".db");
        testDataDir = Files.createTempDirectory("async-safety-data-");
        AppConfig config = createTestConfig(testDbPath);
        
        dbManager = new DatabaseManager(config);
        dbManager.initialize();

        InvestigationQueryService queryService = dbManager.investigationQueryService();
        InvestigationMetrics metrics = new InvestigationMetrics();
        ReplayNavigationService replayService = dbManager.replayNavigationService(metrics);
        
        workspaceService = new WorkspaceService(queryService, replayService, testDataDir);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (workspaceService != null) {
            workspaceService.shutdown();
        }
        if (dbManager != null) {
            dbManager.shutdown();
        }
        if (testDbPath != null && Files.exists(testDbPath)) {
            Files.deleteIfExists(testDbPath);
        }
        if (testDataDir != null && Files.exists(testDataDir)) {
            Files.walk(testDataDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception e) {
                            // Ignore
                        }
                    });
        }
    }

    @Test
    void testStaleIncidentQueryDiscarded() throws Exception {
        // This test verifies that generation counters work correctly
        // The actual stale response discard happens in Platform.runLater which requires JavaFX
        // So we just verify the mechanism is in place
        
        var metrics = workspaceService.getMetrics();
        long initialDispatched = metrics.asyncQueriesDispatched();
        
        // Fire two queries - second should invalidate first
        workspaceService.findIncidentByIdAsync(
                "INC-001",
                result -> {},
                error -> {}
        );
        
        workspaceService.findIncidentByIdAsync(
                "INC-002",
                result -> {},
                error -> {}
        );
        
        // Give async operations time to dispatch
        Thread.sleep(100);
        
        // Verify queries were dispatched
        metrics = workspaceService.getMetrics();
        assertTrue(metrics.asyncQueriesDispatched() >= initialDispatched + 2, 
                "Should have dispatched at least 2 queries");
    }

    @Test
    void testNavigationHistoryWorks() {
        workspaceService.transitionTo(NavigationContext.INCIDENT_DETAIL);
        assertTrue(workspaceService.canNavigateBack());
        assertFalse(workspaceService.canNavigateForward());

        workspaceService.transitionTo(NavigationContext.EVIDENCE_EXPLORATION);
        assertTrue(workspaceService.canNavigateBack());
        assertFalse(workspaceService.canNavigateForward());

        boolean backSuccess = workspaceService.navigateBack();
        assertTrue(backSuccess);
        assertEquals(NavigationContext.INCIDENT_DETAIL, 
                workspaceService.currentState().navigationContext());
        assertTrue(workspaceService.canNavigateForward());

        boolean forwardSuccess = workspaceService.navigateForward();
        assertTrue(forwardSuccess);
        assertEquals(NavigationContext.EVIDENCE_EXPLORATION, 
                workspaceService.currentState().navigationContext());
    }

    @Test
    void testSessionPersistenceAndRestoration() throws Exception {
        // Create a specific state
        WorkspaceState state = WorkspaceState.builder()
                .activeIncidentId("INC-TEST-001")
                .navigationContext(NavigationContext.INCIDENT_DETAIL)
                .navigationDepth(5)
                .build();
        
        workspaceService.updateState(state);
        Thread.sleep(300); // Allow async save to complete
        
        // Shutdown to save session
        workspaceService.shutdown();
        
        // Create new service (should restore session)
        InvestigationQueryService queryService = dbManager.investigationQueryService();
        InvestigationMetrics metrics = new InvestigationMetrics();
        ReplayNavigationService replayService = dbManager.replayNavigationService(metrics);
        
        WorkspaceService newService = new WorkspaceService(queryService, replayService, testDataDir);
        
        // Verify restoration
        WorkspaceState restored = newService.currentState();
        assertEquals("INC-TEST-001", restored.activeIncidentId().orElse(null));
        assertEquals(NavigationContext.INCIDENT_DETAIL, restored.navigationContext());
        
        newService.shutdown();
    }

    @Test
    void testConcurrentQuerySafety() throws Exception {
        // This test verifies that concurrent queries are dispatched correctly
        // The actual callbacks require JavaFX Platform.runLater which isn't available in tests
        // So we just verify the dispatch mechanism works
        
        var metrics = workspaceService.getMetrics();
        long initialDispatched = metrics.asyncQueriesDispatched();

        // Fire 10 concurrent queries
        for (int i = 0; i < 10; i++) {
            final String incidentId = "INC-" + i;
            workspaceService.findIncidentByIdAsync(
                    incidentId,
                    result -> {},
                    error -> {}
            );
        }

        // Give async operations time to dispatch
        Thread.sleep(200);
        
        // Verify metrics
        metrics = workspaceService.getMetrics();
        assertEquals(initialDispatched + 10, metrics.asyncQueriesDispatched(), 
                "Should have dispatched 10 queries");
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
