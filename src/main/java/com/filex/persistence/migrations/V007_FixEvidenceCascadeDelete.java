package com.filex.persistence.migrations;

import com.filex.persistence.Migration;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Migration: Change CASCADE DELETE to RESTRICT on evidence for forensic integrity.
 *
 * <p>CRITICAL FIX: Evidence records must NEVER be deleted when incidents are deleted. This
 * migration recreates the incident_evidence table with ON DELETE RESTRICT to prevent forensic
 * evidence destruction.
 *
 * <p>Forensic Principle: Evidence must be immutable and permanent for legal/audit purposes.
 * Incidents can be closed/dismissed, but evidence must remain for compliance.
 */
public final class V007_FixEvidenceCascadeDelete implements Migration {

  @Override
  public int version() {
    return 7;
  }

  @Override
  public String description() {
    return "Fix CASCADE DELETE on evidence - change to RESTRICT for forensic integrity";
  }

  @Override
  public void migrate(Connection connection) throws SQLException {
    try (Statement stmt = connection.createStatement()) {

      // Step 1: Create new table with correct foreign key constraint
      stmt.execute(
          """
                    CREATE TABLE IF NOT EXISTS incident_evidence_new (
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
                        FOREIGN KEY (incident_id) REFERENCES incidents(incident_id) ON DELETE RESTRICT
                    );
                    """);

      // Step 2: Copy existing data
      stmt.execute(
          """
                    INSERT INTO incident_evidence_new
                    SELECT * FROM incident_evidence;
                    """);

      // Step 3: Drop old table
      stmt.execute("DROP TABLE incident_evidence;");

      // Step 4: Rename new table
      stmt.execute("ALTER TABLE incident_evidence_new RENAME TO incident_evidence;");

      // Step 5: Recreate indexes
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
    }
  }
}
