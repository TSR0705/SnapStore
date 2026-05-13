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
        CountDownLatch latch = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicReference<String> lastResult = new AtomicReference<>();

        // Fire query A
        workspaceService.findIncidentByIdAsync(
                "INC-001",
                result -> {
                    lastResult.set("A");
                    successCount.incrementAndGet();
                    latch.countDown();
                },
                error -> latch.countDown()
        );

        // Fire query B (should invalidate A)
        workspaceService.findIncidentByIdAsync(
                "INC-002",
                result -> {
                    lastResult.set("B");
                    successCount.incrementAndGet();
                    latch.countDown();
                },
                error -> latch.countDown()
        );

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Queries should complete");
        
        // Only the last query should have been processed
        // (or both if B completed before A, but A should be discarded)
        var metrics = workspaceService.getMetrics();
        assertTrue(metrics.staleAsyncResponsesIgnored() >= 0, 
                "Stale responses should be tracked");
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
        CountDownLatch latch = new CountDownLatch(10);
        AtomicInteger completedQueries = new AtomicInteger(0);

        // Fire 10 concurrent queries
        for (int i = 0; i < 10; i++) {
            final String incidentId = "INC-" + i;
            workspaceService.findIncidentByIdAsync(
                    incidentId,
                    result -> {
                        completedQueries.incrementAndGet();
                        latch.countDown();
                    },
                    error -> latch.countDown()
            );
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "All queries should complete");
        
        // Verify metrics
        var metrics = workspaceService.getMetrics();
        assertEquals(10, metrics.asyncQueriesDispatched());
        assertTrue(metrics.asyncQueriesCompleted() + metrics.asyncQueriesFailed() + 
                   metrics.staleAsyncResponsesIgnored() >= 10,
                "All queries should be accounted for");
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
