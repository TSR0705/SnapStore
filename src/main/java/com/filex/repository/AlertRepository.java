package com.filex.repository;

import com.filex.model.AlertEntity;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Repository for {@link AlertEntity} persistence operations.
 *
 * <p>Manages alert records generated from suspicious file activity. All SQL is encapsulated here
 * with PreparedStatement for safety.
 */
public final class AlertRepository {

  private static final Logger log = LoggerFactory.getLogger(AlertRepository.class);

  private final Connection connection;

  public AlertRepository(Connection connection) {
    this.connection = connection;
  }

  /**
   * Inserts a new alert.
   *
   * @return the generated ID
   */
  public long insert(AlertEntity alert) throws SQLException {
    String sql =
        """
                INSERT INTO alerts (
                    alert_id, timestamp, severity, alert_type, title, description,
                    file_event_id, acknowledged, acknowledged_at, acknowledged_by, metadata
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

    try (PreparedStatement pstmt =
        connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
      pstmt.setString(1, alert.getAlertId());
      pstmt.setLong(2, alert.getTimestamp().toEpochMilli());
      pstmt.setString(3, alert.getSeverity());
      pstmt.setString(4, alert.getAlertType());
      pstmt.setString(5, alert.getTitle());
      pstmt.setString(6, alert.getDescription());
      pstmt.setString(7, alert.getFileEventId());
      pstmt.setInt(8, alert.isAcknowledged() ? 1 : 0);
      pstmt.setObject(
          9, alert.getAcknowledgedAt() != null ? alert.getAcknowledgedAt().toEpochMilli() : null);
      pstmt.setString(10, alert.getAcknowledgedBy());
      pstmt.setString(11, alert.getMetadata());

      pstmt.executeUpdate();

      try (ResultSet rs = pstmt.getGeneratedKeys()) {
        if (rs.next()) {
          long id = rs.getLong(1);
          log.debug("Inserted alert: id={}, alertId={}", id, alert.getAlertId());
          return id;
        }
      }
    }

    throw new SQLException("Failed to retrieve generated ID for alert");
  }

  /** Finds an alert by its unique alert ID. */
  public Optional<AlertEntity> findByAlertId(String alertId) throws SQLException {
    String sql = "SELECT * FROM alerts WHERE alert_id = ?";

    try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
      pstmt.setString(1, alertId);

      try (ResultSet rs = pstmt.executeQuery()) {
        if (rs.next()) {
          return Optional.of(mapRow(rs));
        }
      }
    }

    return Optional.empty();
  }

  /** Finds all alerts, paginated and ordered by timestamp descending. */
  public Page<AlertEntity> findAll(PageRequest pageRequest) throws SQLException {
    List<AlertEntity> content = new ArrayList<>();

    String sql = "SELECT * FROM alerts ORDER BY timestamp DESC LIMIT ? OFFSET ?";

    try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
      pstmt.setInt(1, pageRequest.limit());
      pstmt.setInt(2, pageRequest.offset());

      try (ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) {
          content.add(mapRow(rs));
        }
      }
    }

    long total = count();
    return new Page<>(content, pageRequest.page(), pageRequest.size(), total);
  }

  /** Finds unacknowledged alerts, paginated. */
  public Page<AlertEntity> findUnacknowledged(PageRequest pageRequest) throws SQLException {
    List<AlertEntity> content = new ArrayList<>();

    String sql =
        "SELECT * FROM alerts WHERE acknowledged = 0 ORDER BY timestamp DESC LIMIT ? OFFSET ?";

    try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
      pstmt.setInt(1, pageRequest.limit());
      pstmt.setInt(2, pageRequest.offset());

      try (ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) {
          content.add(mapRow(rs));
        }
      }
    }

    long total = countUnacknowledged();
    return new Page<>(content, pageRequest.page(), pageRequest.size(), total);
  }

  /** Finds alerts by severity, paginated. */
  public Page<AlertEntity> findBySeverity(String severity, PageRequest pageRequest)
      throws SQLException {
    List<AlertEntity> content = new ArrayList<>();

    String sql = "SELECT * FROM alerts WHERE severity = ? ORDER BY timestamp DESC LIMIT ? OFFSET ?";

    try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
      pstmt.setString(1, severity);
      pstmt.setInt(2, pageRequest.limit());
      pstmt.setInt(3, pageRequest.offset());

      try (ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) {
          content.add(mapRow(rs));
        }
      }
    }

    long total = countBySeverity(severity);
    return new Page<>(content, pageRequest.page(), pageRequest.size(), total);
  }

  /** Acknowledges an alert. */
  public void acknowledge(String alertId, String acknowledgedBy) throws SQLException {
    String sql =
        """
                UPDATE alerts
                SET acknowledged = 1,
                    acknowledged_at = ?,
                    acknowledged_by = ?
                WHERE alert_id = ?
                """;

    try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
      pstmt.setLong(1, Instant.now().toEpochMilli());
      pstmt.setString(2, acknowledgedBy);
      pstmt.setString(3, alertId);

      int updated = pstmt.executeUpdate();
      if (updated > 0) {
        log.debug("Acknowledged alert: alertId={}, by={}", alertId, acknowledgedBy);
      }
    }
  }

  /** Counts total alerts. */
  public long count() throws SQLException {
    String sql = "SELECT COUNT(*) FROM alerts";

    try (PreparedStatement pstmt = connection.prepareStatement(sql);
        ResultSet rs = pstmt.executeQuery()) {
      if (rs.next()) {
        return rs.getLong(1);
      }
    }

    return 0;
  }

  /** Counts unacknowledged alerts. */
  public long countUnacknowledged() throws SQLException {
    String sql = "SELECT COUNT(*) FROM alerts WHERE acknowledged = 0";

    try (PreparedStatement pstmt = connection.prepareStatement(sql);
        ResultSet rs = pstmt.executeQuery()) {
      if (rs.next()) {
        return rs.getLong(1);
      }
    }

    return 0;
  }

  /** Counts alerts by severity. */
  public long countBySeverity(String severity) throws SQLException {
    String sql = "SELECT COUNT(*) FROM alerts WHERE severity = ?";

    try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
      pstmt.setString(1, severity);

      try (ResultSet rs = pstmt.executeQuery()) {
        if (rs.next()) {
          return rs.getLong(1);
        }
      }
    }

    return 0;
  }

  // -------------------------------------------------------------------------
  // Private helpers
  // -------------------------------------------------------------------------

  private AlertEntity mapRow(ResultSet rs) throws SQLException {
    // Handle nullable Instant for acknowledged_at
    Instant acknowledgedAt = null;
    long acknowledgedAtValue = rs.getLong("acknowledged_at");
    if (!rs.wasNull()) {
      acknowledgedAt = Instant.ofEpochMilli(acknowledgedAtValue);
    }

    long timestampMillis = rs.getLong("timestamp");
    Instant timestamp = Instant.ofEpochMilli(timestampMillis);

    long createdAtMillis = rs.getLong("created_at");
    Instant createdAt = Instant.ofEpochMilli(createdAtMillis);

    return AlertEntity.builder()
        .id(rs.getLong("id"))
        .alertId(rs.getString("alert_id"))
        .timestamp(timestamp)
        .severity(rs.getString("severity"))
        .alertType(rs.getString("alert_type"))
        .title(rs.getString("title"))
        .description(rs.getString("description"))
        .fileEventId(rs.getString("file_event_id"))
        .acknowledged(rs.getInt("acknowledged") == 1)
        .acknowledgedAt(acknowledgedAt)
        .acknowledgedBy(rs.getString("acknowledged_by"))
        .metadata(rs.getString("metadata"))
        .createdAt(createdAt)
        .build();
  }
}
