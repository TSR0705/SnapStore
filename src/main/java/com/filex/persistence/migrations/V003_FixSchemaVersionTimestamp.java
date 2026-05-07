package com.filex.persistence.migrations;

import com.filex.persistence.Migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Fixes schema_version.applied_at timestamp format.
 *
 * <p>Migrates from TEXT datetime format to INTEGER epoch milliseconds
 * for consistency with all other tables.
 *
 * <p>This migration is idempotent and safe to run on both:
 * <ul>
 *   <li>Fresh databases (where schema_version already uses INTEGER)</li>
 *   <li>Existing databases (where schema_version uses TEXT)</li>
 * </ul>
 */
public final class V003_FixSchemaVersionTimestamp implements Migration {

    @Override
    public int version() {
        return 3;
    }

    @Override
    public String description() {
        return "Fix schema_version.applied_at to use INTEGER epoch milliseconds";
    }

    @Override
    public void migrate(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            
            // Check if applied_at is TEXT or INTEGER
            // SQLite doesn't have ALTER COLUMN, so we need to recreate the table
            
            // Create new table with correct schema
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS schema_version_new (
                        id          INTEGER PRIMARY KEY AUTOINCREMENT,
                        version     INTEGER NOT NULL UNIQUE,
                        description TEXT    NOT NULL,
                        applied_at  INTEGER NOT NULL DEFAULT (strftime('%s', 'now') * 1000)
                    );
                    """);
            
            // Copy data, converting TEXT timestamps to INTEGER if needed
            // If applied_at is already INTEGER, this will work fine
            // If applied_at is TEXT, we convert it to epoch milliseconds
            stmt.execute("""
                    INSERT INTO schema_version_new (id, version, description, applied_at)
                    SELECT 
                        id, 
                        version, 
                        description,
                        CASE 
                            WHEN typeof(applied_at) = 'integer' THEN applied_at
                            ELSE strftime('%s', applied_at) * 1000
                        END as applied_at
                    FROM schema_version
                    WHERE version NOT IN (SELECT version FROM schema_version_new);
                    """);
            
            // Drop old table
            stmt.execute("DROP TABLE schema_version;");
            
            // Rename new table
            stmt.execute("ALTER TABLE schema_version_new RENAME TO schema_version;");
        }
    }
}
