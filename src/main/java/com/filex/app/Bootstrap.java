package com.filex.app;

import com.filex.alert.AlertEngine;
import com.filex.alert.IncidentPersistenceSubscriber;
import com.filex.config.AppConfig;
import com.filex.config.ConfigManager;
import com.filex.database.DatabaseManager;
import com.filex.detection.DetectionEngine;
import com.filex.engine.MonitoringEngine;
import com.filex.event.EventBus;
import com.filex.investigation.InvestigationMetrics;
import com.filex.investigation.InvestigationQueryService;
import com.filex.investigation.ReplayNavigationService;
import com.filex.persistence.IncidentPersistenceService;
import com.filex.ui.ViewManager;
import com.filex.workspace.WorkspaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Application bootstrap orchestrator.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Initialize all core infrastructure components in the correct order
 *   <li>Construct the {@link AppContext} dependency container
 *   <li>Fail fast if any initialization step fails
 *   <li>Log the bootstrap sequence for diagnostics
 * </ul>
 *
 * <p>Bootstrap order (critical — do not reorder):
 *
 * <ol>
 *   <li>Configuration resolution (creates app directories)
 *   <li>Database initialization (opens SQLite connection, runs DDL)
 *   <li>Event bus creation
 *   <li>Monitoring engine creation
 *   <li>Detection engine creation
 *   <li>Alert engine creation
 *   <li>Incident persistence service creation
 *   <li>Incident persistence subscriber creation
 *   <li>Investigation services creation
 *   <li>Workspace service creation
 *   <li>View manager creation
 *   <li>AppContext assembly
 * </ol>
 *
 * <p>This class is stateless and non-instantiable. Use {@link #initialize()}.
 */
public final class Bootstrap {

  private static final Logger log = LoggerFactory.getLogger(Bootstrap.class);

  private Bootstrap() {
    // Non-instantiable utility
  }

  /**
   * Executes the full bootstrap sequence and returns the initialized {@link AppContext}.
   *
   * @return fully initialized application context
   * @throws BootstrapException if any initialization step fails
   */
  public static AppContext initialize() {
    log.info("========================================");
    log.info("FileX Application Bootstrap Starting");
    log.info("========================================");

    try {
      // Step 1: Resolve configuration
      log.info("[1/10] Resolving configuration...");
      AppConfig config = ConfigManager.resolve();
      log.info("Configuration resolved: {}", config.summary());

      // Step 2: Initialize database
      log.info("[2/10] Initializing database...");
      DatabaseManager databaseManager = new DatabaseManager(config);
      databaseManager.initialize();
      log.info("Database initialized: {}", config.databaseFile());

      // Step 3: Create event bus
      log.info("[3/10] Creating event bus...");
      EventBus eventBus = new EventBus();
      log.info("Event bus created.");

      // Step 4: Create monitoring engine
      log.info("[4/10] Creating monitoring engine...");
      MonitoringEngine monitoringEngine = new MonitoringEngine(eventBus);
      log.info("Monitoring engine created.");

      // Step 5: Create detection engine
      log.info("[5/10] Creating detection engine...");
      DetectionEngine detectionEngine = new DetectionEngine(eventBus);
      log.info("Detection engine created.");

      // Step 6: Create alert engine
      log.info("[6/10] Creating alert engine...");
      AlertEngine alertEngine = new AlertEngine(eventBus);
      log.info("Alert engine created.");

      // Step 7: Create incident persistence service
      log.info("[7/10] Creating incident persistence service...");
      IncidentPersistenceService incidentPersistenceService =
          databaseManager.incidentPersistenceService();
      log.info("Incident persistence service created.");

      // Step 8: Create incident persistence subscriber
      log.info("[8/10] Creating incident persistence subscriber...");
      IncidentPersistenceSubscriber incidentPersistenceSubscriber =
          new IncidentPersistenceSubscriber(eventBus, incidentPersistenceService);
      log.info("Incident persistence subscriber created.");

      // Step 9: Create investigation services
      log.info("[9/10] Creating investigation services...");
      InvestigationQueryService queryService = databaseManager.investigationQueryService();
      InvestigationMetrics investigationMetrics = new InvestigationMetrics();
      ReplayNavigationService replayService =
          databaseManager.replayNavigationService(investigationMetrics);
      log.info("Investigation services created.");

      // Step 9: Create workspace service
      log.info("[9/10] Creating workspace service...");
      WorkspaceService workspaceService =
          new WorkspaceService(queryService, replayService, config.dataDir());
      log.info("Workspace service created.");

      // Step 10: Create AppContext and ViewManager
      log.info("[10/10] Creating application context and view manager...");
      // Create AppContext with core infrastructure (no ViewManager/WorkspaceService yet)
      AppContext appContext =
          new AppContext(
              config,
              databaseManager,
              eventBus,
              monitoringEngine,
              detectionEngine,
              alertEngine,
              incidentPersistenceService,
              incidentPersistenceSubscriber);

      // Create ViewManager with the AppContext
      ViewManager viewManager = new ViewManager(appContext);

      // Register ViewManager and WorkspaceService with AppContext (breaks circular dependencies)
      appContext.setViewManager(viewManager);
      appContext.setWorkspaceService(workspaceService);
      log.info("Application context and view manager created.");

      log.info("========================================");
      log.info("Bootstrap Complete: {}", appContext.summary());
      log.info("========================================");

      return appContext;

    } catch (Exception e) {
      log.error("========================================");
      log.error("BOOTSTRAP FAILED: {}", e.getMessage(), e);
      log.error("========================================");
      throw new BootstrapException("Application bootstrap failed. See logs for details.", e);
    }
  }
}
