package com.filex.persistence.migrations;

import com.filex.persistence.Migration;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Migration: Create incident persistence and forensic timeline tables.
 *
 * <p>Creates tables for:
 *
 * <ul>
 *   <li>incidents — correlated security incidents
 *   <li>incident_evidence — immutable evidence chain linking detections to incidents
 *   <li>forensic_timeline — chronological reconstruction of security events
 * </ul>
 *
 * <p>Design principles:
 *
 * <ul>
 *   <li>Incidents are mutable (status, severity can escalate)
 *   <li>Evidence records are append-only (immutable forensic chain)
 *   <li>Timeline records are immutable (deterministic ordering)
 *   <li>All timestamps stored as epoch milliseconds (INTEGER)
 *   <li>Foreign keys enforce referential integrity
 * </ul>
 */
public final class V005_CreateIncidentTables implements Migration {

  @Override
  public int version() {
    return 5;
  }

  @Override
  public String description() {
    return "Create incident persistence and forensic timeline tables";
  }

  @Override
  public void migrate(Connection connection) throws SQLException {
    try (Statement stmt = connection.createStatement()) {

      // Incidents table - correlated security incidents
      stmt.execute(
          """
                    CREATE TABLE IF NOT EXISTS incidents (
                        id                  INTEGER PRIMARY KEY AUTOINCREMENT,
                        incident_id         TEXT    NOT NULL UNIQUE,
                        severity            TEXT    NOT NULL,
                        confidence          TEXT    NOT NULL,
                        status              TEXT    NOT NULL,
                        title               TEXT    NOT NULL,
                        description         TEXT,
                        created_at          INTEGER NOT NULL,
                        updated_at          INTEGER NOT NULL,
                        last_seen_at        INTEGER NOT NULL,
                        correlation_id      TEXT,
                        escalation_level    INTEGER NOT NULL DEFAULT 0,
                        detection_count     INTEGER NOT NULL DEFAULT 1,
                        metadata            TEXT
                    );
                    """);

      // Incident evidence table - immutable evidence chain
      stmt.execute(
          """
                    CREATE TABLE IF NOT EXISTS incident_evidence (
                        id                  INTEGER PRIMARY KEY AUTOINCREMENT,
                        evidence_id         TEXT    NOT NULL UNIQUE,
                        incident_id         TEXT    NOT NULL,
                        detection_id        TEXT    NOT NULL,
                        rule_name           TEXT    NOT NULL,
                        severity            TEXT    NOT NULL,
                        confidence          TEXT    NOT NULL,
                        file_path           TEXT,
                        detected_at         INTEGER NOT NULL,
                        correlation_id      TEXT,
                        metadata            TEXT,
                        created_at          INTEGER NOT NULL DEFAULT (strftime('%s', 'now') * 1000),
                        FOREIGN KEY (incident_id) REFERENCES incidents(incident_id) ON DELETE CASCADE
                    );
                    """);

      // Forensic timeline table - chronological event reconstruction
      stmt.execute(
          """
                    CREATE TABLE IF NOT EXISTS forensic_timeline (
                        id                  INTEGER PRIMARY KEY AUTOINCREMENT,
                        timeline_id         TEXT    NOT NULL UNIQUE,
                        timestamp           INTEGER NOT NULL,
                        sequence_number     INTEGER NOT NULL,
                        event_type          TEXT    NOT NULL,
                        incident_id         TEXT,
                        evidence_id         TEXT,
                        detection_id        TEXT,
                        severity            TEXT,
                        description         TEXT,
                        correlation_id      TEXT,
                        metadata            TEXT,
                        created_at          INTEGER NOT NULL DEFAULT (strftime('%s', 'now') * 1000),
                        FOREIGN KEY (incident_id) REFERENCES incidents(incident_id) ON DELETE SET NULL,
                        FOREIGN KEY (evidence_id) REFERENCES incident_evidence(evidence_id) ON DELETE SET NULL
                    );
                    """);
    }
  }
}
