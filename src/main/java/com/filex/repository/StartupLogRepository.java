package com.filex.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Repository for application startup audit log.
 *
 * <p>Records application startup events for audit and diagnostics. All SQL is encapsulated here
 * with PreparedStatement for safety.
 */
public final class StartupLogRepository {

  private static final Logger log = LoggerFactory.getLogger(StartupLogRepository.class);

  private final Connection connection;

  public StartupLogRepository(Connection connection) {
    this.connection = connection;
  }

  /**
   * Records an application startup event.
   *
   * @param appVersion application version
   * @param hostname machine hostname
   * @param osName operating system name
   * @param javaVersion Java runtime version
   * @return the generated ID
   */
  public long recordStartup(String appVersion, String hostname, String osName, String javaVersion)
      throws SQLException {
    String sql =
        "INSERT INTO app_startup_log (app_version, hostname, os_name, java_version) VALUES (?, ?, ?, ?)";

    try (PreparedStatement pstmt =
        connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
      pstmt.setString(1, appVersion);
      pstmt.setString(2, hostname);
      pstmt.setString(3, osName);
      pstmt.setString(4, javaVersion);
      pstmt.executeUpdate();

      try (var rs = pstmt.getGeneratedKeys()) {
        if (rs.next()) {
          long id = rs.getLong(1);
          log.debug("Startup record inserted: id={}, version={}", id, appVersion);
          return id;
        }
      }
    }

    throw new SQLException("Failed to retrieve generated ID for startup log");
  }
}
