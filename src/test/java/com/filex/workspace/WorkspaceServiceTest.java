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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link WorkspaceService}.
 */
class WorkspaceServiceTest {

    private DatabaseManager dbManager;
    private Path testDbPath;
    private WorkspaceService workspaceService;

    @BeforeEach
    void setUp() throws Exception {
        testDbPath = Files.createTempFile("workspace-test-", ".db");
        Path testDataDir = Files.createTempDirectory("workspace-data-");
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
    }

    @Test
    void testInitialState() {
        WorkspaceState state = workspaceService.currentState();
        
        assertNotNull(state);
        assertEquals(NavigationContext.INCIDENT_LIST, state.navigationContext());
        assertFalse(state.hasActiveIncident());
        assertFalse(state.hasActiveEvidence());
        assertFalse(state.hasActiveReplay());
        assertEquals(0, state.navigationDepth());
    }

    @Test
    void testStateTransition() {
        workspaceService.transitionTo(NavigationContext.INCIDENT_DETAIL);
        
        WorkspaceState state = workspaceService.currentState();
        assertEquals(NavigationContext.INCIDENT_DETAIL, state.navigationContext());
        assertEquals(1, state.navigationDepth());
    }

    @Test
    void testStateUpdate() {
        WorkspaceState newState = WorkspaceState.builder()
                .activeIncidentId("INC-001")
                .navigationContext(NavigationContext.INCIDENT_DETAIL)
                .build();
        
        workspaceService.updateState(newState);
        
        WorkspaceState currentState = workspaceService.currentState();
        assertTrue(currentState.hasActiveIncident());
        assertEquals("INC-001", currentState.activeIncidentId().orElse(null));
        assertEquals(NavigationContext.INCIDENT_DETAIL, currentState.navigationContext());
    }

    @Test
    void testMetricsTracking() {
        workspaceService.transitionTo(NavigationContext.INCIDENT_DETAIL);
        workspaceService.transitionTo(NavigationContext.EVIDENCE_EXPLORATION);
        
        var metrics = workspaceService.getMetrics();
        
        assertTrue(metrics.navigationOperations() >= 2);
        assertTrue(metrics.navigationLatencyMs() >= 0);
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
