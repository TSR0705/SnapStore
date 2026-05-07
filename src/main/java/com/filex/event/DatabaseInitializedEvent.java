package com.filex.event;

/**
 * Event published when the database has been successfully initialized.
 *
 * <p>Indicates that migrations have run and the database is ready
 * for use by repositories and services.
 */
public final class DatabaseInitializedEvent extends AppEvent {

    public DatabaseInitializedEvent() {
        super("DatabaseManager");
    }
}
