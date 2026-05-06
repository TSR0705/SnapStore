package com.filex.persistence.migrations;

import com.filex.persistence.Migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Initial schema migration.
 *
 * <p>Creates the core tables for FileX:
 * <ul>
 *   <li>file_events — immutable file activity event log</li>
 *   <li>alerts — generated alerts from suspicious activity</li>
 *   <li>file_fingerprints — file hash/metadata cache</li>
 *   <li>sync_queue — offline-first sync queue for backend</li>
 *   <li>app_settings — persistent application settings</li>
 *   <li>app_startup_log — application startup audit trail</li>
 * </ul>
 */
public final class V001_InitialSchema implements Migration {

    @Override
    public int version() {
        return 1;
    }

    @Override
    public String description() {
        return "Initial schema: file_events, alerts, fingerprints, sync_queue, settings";
    }

    @Override
    public void migrate(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            
            // File events table - immutable event log
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS file_events (
                        id              INTEGER PRIMARY KEY AUTOINCREMENT,
                        event_id        TEXT    NOT NULL UNIQUE,
                        timestamp       INTEGER NOT NULL,
                        event_type      TEXT    NOT NULL,
                        file_path       TEXT    NOT NULL,
                        file_size       INTEGER,
                        file_hash       TEXT,
                        process_name    TEXT,
                        process_id      INTEGER,
                        user_name       TEXT,
                        suspicious      INTEGER NOT NULL DEFAULT 0,
                        risk_score      REAL,
                        metadata        TEXT,
                        created_at      INTEGER NOT NULL DEFAULT (strftime('%s', 'now') * 1000)
                    );
                    """);

            // Alerts table - generated alerts
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS alerts (
                        id              INTEGER PRIMARY KEY AUTOINCREMENT,
                        alert_id        TEXT    NOT NULL UNIQUE,
                        timestamp       INTEGER NOT NULL,
                        severity        TEXT    NOT NULL,
                        alert_type      TEXT    NOT NULL,
                        title           TEXT    NOT NULL,
                        description     TEXT,
                        file_event_id   TEXT,
                        acknowledged    INTEGER NOT NULL DEFAULT 0,
                        acknowledged_at INTEGER,
                        acknowledged_by TEXT,
                        metadata        TEXT,
                        created_at      INTEGER NOT NULL DEFAULT (strftime('%s', 'now') * 1000),
                        FOREIGN KEY (file_event_id) REFERENCES file_events(event_id)
                    );
                    """);

            // File fingerprints table - hash/metadata cache
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS file_fingerprints (
                        id              INTEGER PRIMARY KEY AUTOINCREMENT,
                        file_path       TEXT    NOT NULL UNIQUE,
                        file_hash       TEXT    NOT NULL,
                        file_size       INTEGER NOT NULL,
                        last_modified   INTEGER NOT NULL,
                        first_seen      INTEGER NOT NULL DEFAULT (strftime('%s', 'now') * 1000),
                        last_seen       INTEGER NOT NULL DEFAULT (strftime('%s', 'now') * 1000),
                        scan_count      INTEGER NOT NULL DEFAULT 1,
                        metadata        TEXT
                    );
                    """);

            // Sync queue table - offline-first sync support
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS sync_queue (
                        id              INTEGER PRIMARY KEY AUTOINCREMENT,
                        entity_type     TEXT    NOT NULL,
                        entity_id       TEXT    NOT NULL,
                        operation       TEXT    NOT NULL,
                        payload         TEXT    NOT NULL,
                        status          TEXT    NOT NULL DEFAULT 'pending',
                        retry_count     INTEGER NOT NULL DEFAULT 0,
                        last_attempt    INTEGER,
                        error_message   TEXT,
                        created_at      INTEGER NOT NULL DEFAULT (strftime('%s', 'now') * 1000),
                        synced_at       INTEGER
                    );
                    """);

            // App settings table - persistent configuration
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS app_settings (
                        key             TEXT    PRIMARY KEY,
                        value           TEXT    NOT NULL,
                        value_type      TEXT    NOT NULL,
                        description     TEXT,
                        updated_at      INTEGER NOT NULL DEFAULT (strftime('%s', 'now') * 1000)
                    );
                    """);

            // App startup log table - audit trail
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS app_startup_log (
                        id              INTEGER PRIMARY KEY AUTOINCREMENT,
                        started_at      INTEGER NOT NULL DEFAULT (strftime('%s', 'now') * 1000),
                        app_version     TEXT    NOT NULL,
                        hostname        TEXT,
                        os_name         TEXT,
                        java_version    TEXT
                    );
                    """);
        }
    }
}
