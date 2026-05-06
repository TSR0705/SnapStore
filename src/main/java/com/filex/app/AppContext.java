package com.filex.app;

import com.filex.config.AppConfig;
import com.filex.database.DatabaseManager;
import com.filex.event.EventBus;
import com.filex.ui.ViewManager;

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
 *   <li>Passed to controllers via dependency injection</li>
 *   <li>Held by {@link FileXApplication} until shutdown</li>
 * </ol>
 *
 * <p>All fields except ViewManager are final and set at construction.
 * ViewManager is set once during bootstrap to break circular dependency.
 */
public final class AppContext {

    private final AppConfig config;
    private final DatabaseManager databaseManager;
    private final EventBus eventBus;
    
    /** Set once during bootstrap via setViewManager(). */
    private ViewManager viewManager;

    /**
     * Package-private constructor — only {@link Bootstrap} should create instances.
     * ViewManager is set separately via {@link #setViewManager(ViewManager)}.
     */
    AppContext(
            AppConfig config,
            DatabaseManager databaseManager,
            EventBus eventBus
    ) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager must not be null");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus must not be null");
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
     * Returns a human-readable summary of the context state.
     * Useful for startup logging.
     */
    public String summary() {
        return String.format(
                "[AppContext] config=%s dbConnected=%b eventBusReady=%b viewManagerReady=%b",
                config.appName(),
                databaseManager.isConnected(),
                eventBus != null,
                viewManager != null
        );
    }
}
