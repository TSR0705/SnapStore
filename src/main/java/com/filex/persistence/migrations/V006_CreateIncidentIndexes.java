package com.filex.persistence.migrations;

import com.filex.persistence.Migration;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Migration: Create indexes for incident and timeline queries.
 *
 * <p>Creates indexes for:
 *
 * <ul>
 *   <li>Incident lookup by ID, status, severity, correlation
 *   <li>Evidence lookup by incident, detection, rule
 *   <li>Timeline ordering and range queries
 *   <li>Correlation-based queries
 * </ul>
 *
 * <p>Index strategy:
 *
 * <ul>
 *   <li>Composite indexes for common query patterns
 *   <li>Timestamp indexes for time-range queries
 *   <li>Foreign key indexes for join performance
 *   <li>Sequence number index for deterministic ordering
 * </ul>
 */
public final class V006_CreateIncidentIndexes implements Migration {

  @Override
  public int version() {
    return 6;
  }

  @Override
  public String description() {
    return "Create indexes for incident and timeline queries";
  }

  @Override
  public void migrate(Connection connection) throws SQLException {
    try (Statement stmt = connection.createStatement()) {

      // Incident indexes
      stmt.execute("CREATE INDEX IF NOT EXISTS idx_incidents_status ON incidents(status);");
      stmt.execute("CREATE INDEX IF NOT EXISTS idx_incidents_severity ON incidents(severity);");
      stmt.execute(
          "CREATE INDEX IF NOT EXISTS idx_incidents_created_at ON incidents(created_at DESC);");
      stmt.execute(
          "CREATE INDEX IF NOT EXISTS idx_incidents_updated_at ON incidents(updated_at DESC);");
      stmt.execute(
          "CREATE INDEX IF NOT EXISTS idx_incidents_correlation_id ON incidents(correlation_id);");
      stmt.execute(
          "CREATE INDEX IF NOT EXISTS idx_incidents_status_severity ON incidents(status, severity);");

      // Evidence indexes
      stmt.execute(
          "CREATE INDEX IF NOT EXISTS idx_evidence_incident_id ON incident_evidence(incident_id);");
      stmt.execute(
          "CREATE INDEX IF NOT EXISTS idx_evidence_detection_id ON incident_evidence(detection_id);");
      stmt.execute(
          "CREATE INDEX IF NOT EXISTS idx_evidence_rule_name ON incident_evidence(rule_name);");
      stmt.execute(
          "CREATE INDEX IF NOT EXISTS idx_evidence_detected_at ON incident_evidence(detected_at DESC);");
      stmt.execute(
          "CREATE INDEX IF NOT EXISTS idx_evidence_correlation_id ON incident_evidence(correlation_id);");
      stmt.execute(
          "CREATE INDEX IF NOT EXISTS idx_evidence_file_path ON incident_evidence(file_path);");

      // Timeline indexes - critical for chronological reconstruction
      stmt.execute(
          "CREATE INDEX IF NOT EXISTS idx_timeline_timestamp_seq ON forensic_timeline(timestamp DESC, sequence_number DESC);");
      stmt.execute(
          "CREATE INDEX IF NOT EXISTS idx_timeline_incident_id ON forensic_timeline(incident_id);");
      stmt.execute(
          "CREATE INDEX IF NOT EXISTS idx_timeline_event_type ON forensic_timeline(event_type);");
      stmt.execute(
          "CREATE INDEX IF NOT EXISTS idx_timeline_severity ON forensic_timeline(severity);");
      stmt.execute(
          "CREATE INDEX IF NOT EXISTS idx_timeline_correlation_id ON forensic_timeline(correlation_id);");
      stmt.execute(
          "CREATE INDEX IF NOT EXISTS idx_timeline_created_at ON forensic_timeline(created_at DESC);");
    }
  }
}
