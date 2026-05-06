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
 *   <li>Constructed by {@link Bootstrap} after all components are initialized</li>
 *   <li>Passed to {@link ViewManager} and injected into controllers</li>
 *   <li>Held by {@link FileXApplication} until shutdown</li>
 * </ol>
 *
 * <p>This class is immutable after construction. All fields are final.
 */
public final class AppContext {

    private final AppConfig config;
    private final DatabaseManager databaseManager;
    private final EventBus eventBus;
    private final ViewManager viewManager;

    /**
     * Package-private constructor — only {@link Bootstrap} should create instances.
     */
    AppContext(
            AppConfig config,
            DatabaseManager databaseManager,
            EventBus eventBus,
            ViewManager viewManager
    ) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager must not be null");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus must not be null");
        // ViewManager can be null during bootstrap phase
        this.viewManager = viewManager;
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

    /** Returns the view manager. */
    public ViewManager viewManager() {
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
