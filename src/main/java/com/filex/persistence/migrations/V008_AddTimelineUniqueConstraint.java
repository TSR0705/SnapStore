package com.filex.persistence.migrations;

import com.filex.persistence.Migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Migration: Add UNIQUE constraint on forensic_timeline to prevent sequence number race conditions.
 *
 * <p>CRITICAL FIX: Prevents duplicate sequence numbers at the same timestamp,
 * ensuring deterministic timeline ordering under concurrent load.
 *
 * <p>Forensic Principle: Timeline reconstruction must be deterministic and replay-safe.
 * The combination of (timestamp, sequence_number) must be globally unique.
 */
public final class V008_AddTimelineUniqueConstraint implements Migration {

    @Override
    public int version() {
        return 8;
    }

    @Override
    public String description() {
        return "Add UNIQUE constraint on forensic_timeline(timestamp, sequence_number) for deterministic ordering";
    }

    @Override
    public void migrate(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            
            // Create unique index to enforce constraint
            // This prevents race conditions where two threads get the same sequence number
            stmt.execute("""
                    CREATE UNIQUE INDEX IF NOT EXISTS idx_timeline_unique_seq 
                    ON forensic_timeline(timestamp, sequence_number);
                    """);
        }
    }
}
