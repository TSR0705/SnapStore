package com.filex.app;

import com.filex.config.AppConfig;
import com.filex.config.ConfigManager;
import com.filex.database.DatabaseManager;
import com.filex.engine.MonitoringEngine;
import com.filex.event.EventBus;
import com.filex.ui.ViewManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Application bootstrap orchestrator.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Initialize all core infrastructure components in the correct order</li>
 *   <li>Construct the {@link AppContext} dependency container</li>
 *   <li>Fail fast if any initialization step fails</li>
 *   <li>Log the bootstrap sequence for diagnostics</li>
 * </ul>
 *
 * <p>Bootstrap order (critical — do not reorder):
 * <ol>
 *   <li>Configuration resolution (creates app directories)</li>
 *   <li>Database initialization (opens SQLite connection, runs DDL)</li>
 *   <li>Event bus creation</li>
 *   <li>Monitoring engine creation</li>
 *   <li>View manager creation</li>
 *   <li>AppContext assembly</li>
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
     * Executes the full bootstrap sequence and returns the initialized
     * {@link AppContext}.
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
            log.info("[1/5] Resolving configuration...");
            AppConfig config = ConfigManager.resolve();
            log.info("Configuration resolved: {}", config.summary());

            // Step 2: Initialize database
            log.info("[2/5] Initializing database...");
            DatabaseManager databaseManager = new DatabaseManager(config);
            databaseManager.initialize();
            log.info("Database initialized: {}", config.databaseFile());

            // Step 3: Create event bus
            log.info("[3/5] Creating event bus...");
            EventBus eventBus = new EventBus();
            log.info("Event bus created.");

            // Step 4: Create monitoring engine
            log.info("[4/5] Creating monitoring engine...");
            MonitoringEngine monitoringEngine = new MonitoringEngine(eventBus);
            log.info("Monitoring engine created.");

            // Step 5: Create AppContext and ViewManager
            log.info("[5/5] Creating application context and view manager...");
            // Create AppContext with core infrastructure (no ViewManager yet)
            AppContext appContext = new AppContext(config, databaseManager, eventBus, monitoringEngine);
            
            // Create ViewManager with the AppContext
            ViewManager viewManager = new ViewManager(appContext);
            
            // Register ViewManager with AppContext (breaks circular dependency)
            appContext.setViewManager(viewManager);
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
