package com.filex.app;

import com.filex.alert.AlertEngine;
import com.filex.alert.IncidentPersistenceSubscriber;
import com.filex.config.AppConfig;
import com.filex.database.DatabaseManager;
import com.filex.detection.DetectionEngine;
import com.filex.engine.MonitoringEngine;
import com.filex.event.EventBus;
import com.filex.persistence.IncidentPersistenceService;
import com.filex.ui.ViewManager;
import com.filex.workspace.WorkspaceService;

import java.util.Objects;

/**
 * Root dependency container for the FileX application.
 *
 * <p>The {@code AppContext} owns all core infrastructure components and
 * provides them to the rest of the application via explicit getters.
 * This is NOT a service locator — dependencies are passed explicitly
 * through constructors, not pulled from a global static.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>Constructed by {@link Bootstrap} with core infrastructure</li>
 *   <li>ViewManager is set via {@link #setViewManager(ViewManager)} during bootstrap</li>
 *   <li>WorkspaceService is set via {@link #setWorkspaceService(WorkspaceService)} during bootstrap</li>
 *   <li>Passed to controllers via dependency injection</li>
 *   <li>Held by {@link FileXApplication} until shutdown</li>
 * </ol>
 *
 * <p>All fields except ViewManager and WorkspaceService are final and set at construction.
 * ViewManager and WorkspaceService are set once during bootstrap to break circular dependencies.
 */
public final class AppContext {

    private final AppConfig config;
    private final DatabaseManager databaseManager;
    private final EventBus eventBus;
    private final MonitoringEngine monitoringEngine;
    private final DetectionEngine detectionEngine;
    private final AlertEngine alertEngine;
    private final IncidentPersistenceService incidentPersistenceService;
    private final IncidentPersistenceSubscriber incidentPersistenceSubscriber;
    private final com.filex.runtime.RuntimeManager runtimeManager;
    
    /** Set once during bootstrap via setViewManager(). */
    private ViewManager viewManager;
    
    /** Set once during bootstrap via setWorkspaceService(). */
    private WorkspaceService workspaceService;

    /**
     * Package-private constructor — only {@link Bootstrap} should create instances.
     * ViewManager and WorkspaceService are set separately.
     */
    AppContext(
            AppConfig config,
            DatabaseManager databaseManager,
            EventBus eventBus,
            MonitoringEngine monitoringEngine,
            DetectionEngine detectionEngine,
            AlertEngine alertEngine,
            IncidentPersistenceService incidentPersistenceService,
            IncidentPersistenceSubscriber incidentPersistenceSubscriber
    ) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager must not be null");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus must not be null");
        this.monitoringEngine = Objects.requireNonNull(monitoringEngine, "monitoringEngine must not be null");
        this.detectionEngine = Objects.requireNonNull(detectionEngine, "detectionEngine must not be null");
        this.alertEngine = Objects.requireNonNull(alertEngine, "alertEngine must not be null");
        this.incidentPersistenceService = Objects.requireNonNull(incidentPersistenceService, "incidentPersistenceService must not be null");
        this.incidentPersistenceSubscriber = Objects.requireNonNull(incidentPersistenceSubscriber, "incidentPersistenceSubscriber must not be null");
        this.runtimeManager = new com.filex.runtime.RuntimeManager(config, eventBus, monitoringEngine, detectionEngine, alertEngine, incidentPersistenceSubscriber);
    }

    /**
     * Sets the ViewManager. Called once by {@link Bootstrap} after AppContext creation.
     * This breaks the circular dependency between AppContext and ViewManager.
     *
     * @param viewManager the view manager to register
     * @throws IllegalStateException if called more than once
     */
    void setViewManager(ViewManager viewManager) {
        if (this.viewManager != null) {
            throw new IllegalStateException("ViewManager already set. Cannot set twice.");
        }
        this.viewManager = Objects.requireNonNull(viewManager, "viewManager must not be null");
    }

    /**
     * Sets the WorkspaceService. Called once by {@link Bootstrap} after AppContext creation.
     * This breaks the circular dependency between AppContext and WorkspaceService.
     *
     * @param workspaceService the workspace service to register
     * @throws IllegalStateException if called more than once
     */
    void setWorkspaceService(WorkspaceService workspaceService) {
        if (this.workspaceService != null) {
            throw new IllegalStateException("WorkspaceService already set. Cannot set twice.");
        }
        this.workspaceService = Objects.requireNonNull(workspaceService, "workspaceService must not be null");
    }

    /** Returns the resolved application configuration. */
    public AppConfig config() {
        return config;
    }

    /** Returns the database manager. */
    public DatabaseManager databaseManager() {
        return databaseManager;
    }

    /** Returns the application event bus. */
    public EventBus eventBus() {
        return eventBus;
    }

    /** Returns the monitoring engine. */
    public MonitoringEngine monitoringEngine() {
        return monitoringEngine;
    }

    /** Returns the detection engine. */
    public DetectionEngine detectionEngine() {
        return detectionEngine;
    }

    /** Returns the alert engine. */
    public AlertEngine alertEngine() {
        return alertEngine;
    }

    /** Returns the incident persistence service. */
    public IncidentPersistenceService incidentPersistenceService() {
        return incidentPersistenceService;
    }

    /** Returns the incident persistence subscriber. */
    public IncidentPersistenceSubscriber incidentPersistenceSubscriber() {
        return incidentPersistenceSubscriber;
    }

    /** Returns the runtime manager. */
    public com.filex.runtime.RuntimeManager runtimeManager() {
        return runtimeManager;
    }

    /** 
     * Returns the view manager.
     * 
     * @throws IllegalStateException if called before ViewManager is set
     */
    public ViewManager viewManager() {
        if (viewManager == null) {
            throw new IllegalStateException("ViewManager not yet initialized. Called too early in bootstrap.");
        }
        return viewManager;
    }

    /** 
     * Returns the workspace service.
     * 
     * @throws IllegalStateException if called before WorkspaceService is set
     */
    public WorkspaceService workspaceService() {
        if (workspaceService == null) {
            throw new IllegalStateException("WorkspaceService not yet initialized. Called too early in bootstrap.");
        }
        return workspaceService;
    }

    /**
     * Returns a human-readable summary of the context state.
     * Useful for startup logging.
     */
    public String summary() {
        return String.format(
                "[AppContext] config=%s dbConnected=%b eventBusReady=%b monitoringReady=%b detectionReady=%b alertReady=%b persistenceReady=%b runtimeReady=%b viewManagerReady=%b workspaceReady=%b",
                config.appName(),
                databaseManager.isConnected(),
                eventBus != null,
                monitoringEngine != null,
                detectionEngine != null,
                alertEngine != null,
                incidentPersistenceService != null,
                runtimeManager != null,
                viewManager != null,
                workspaceService != null
        );
    }
}
