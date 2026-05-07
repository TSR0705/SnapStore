package com.filex.persistence.migrations;

import com.filex.persistence.Migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates indexes for query performance.
 *
 * <p>Indexes are carefully chosen to balance read performance with
 * write overhead. Focus is on common query patterns:
 * <ul>
 *   <li>Time-range queries on events and alerts</li>
 *   <li>Filtering by event type and severity</li>
 *   <li>Suspicious activity queries</li>
 *   <li>File path lookups</li>
 *   <li>Sync queue status filtering</li>
 * </ul>
 */
public final class V002_CreateIndexes implements Migration {

    @Override
    public int version() {
        return 2;
    }

    @Override
    public String description() {
        return "Create performance indexes on file_events, alerts, and sync_queue";
    }

    @Override
    public void migrate(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            
            // File events indexes
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_file_events_timestamp ON file_events(timestamp DESC);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_file_events_event_type ON file_events(event_type);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_file_events_suspicious ON file_events(suspicious, timestamp DESC);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_file_events_file_path ON file_events(file_path);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_file_events_event_id ON file_events(event_id);");

            // Alerts indexes
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_alerts_timestamp ON alerts(timestamp DESC);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_alerts_severity ON alerts(severity, timestamp DESC);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_alerts_acknowledged ON alerts(acknowledged, timestamp DESC);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_alerts_alert_id ON alerts(alert_id);");

            // File fingerprints indexes
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_fingerprints_file_path ON file_fingerprints(file_path);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_fingerprints_file_hash ON file_fingerprints(file_hash);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_fingerprints_last_seen ON file_fingerprints(last_seen DESC);");

            // Sync queue indexes
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_sync_queue_status ON sync_queue(status, created_at);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_sync_queue_entity ON sync_queue(entity_type, entity_id);");
        }
    }
}
