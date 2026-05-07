package com.filex.persistence.migrations;

import com.filex.persistence.Migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Adds unique constraint to sync_queue for pending operations.
 *
 * <p>Prevents duplicate pending sync operations for the same entity.
 * Uses a partial unique index that only applies to pending operations,
 * allowing multiple completed/failed entries for the same entity.
 *
 * <p>Constraint: (entity_type, entity_id, operation) must be unique
 * when status = 'pending'.
 */
public final class V004_AddSyncQueueUniqueConstraint implements Migration {

    @Override
    public int version() {
        return 4;
    }

    @Override
    public String description() {
        return "Add unique constraint to sync_queue for pending operations";
    }

    @Override
    public void migrate(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            
            // Create partial unique index for pending operations
            // This prevents duplicate pending sync operations
            stmt.execute("""
                    CREATE UNIQUE INDEX IF NOT EXISTS idx_sync_queue_unique_pending
                    ON sync_queue(entity_type, entity_id, operation)
                    WHERE status = 'pending';
                    """);
        }
    }
}
