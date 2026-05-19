package com.filex.repository;

import com.filex.model.FileFingerprintEntity;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Repository for {@link FileFingerprintEntity} persistence operations.
 *
 * <p>Manages file fingerprint cache for deduplication and change detection. All SQL is encapsulated
 * here with PreparedStatement for safety.
 */
public final class FingerprintRepository {

  private static final Logger log = LoggerFactory.getLogger(FingerprintRepository.class);

  private final Connection connection;

  public FingerprintRepository(Connection connection) {
    this.connection = connection;
  }

  /**
   * Inserts a new file fingerprint.
   *
   * @return the generated ID
   */
  public long insert(FileFingerprintEntity fingerprint) throws SQLException {
    String sql =
        """
                INSERT INTO file_fingerprints (
                    file_path, file_hash, file_size, last_modified,
                    first_seen, last_seen, scan_count, metadata
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

    try (PreparedStatement pstmt =
        connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
      pstmt.setString(1, fingerprint.getFilePath());
      pstmt.setString(2, fingerprint.getFileHash());
      pstmt.setLong(3, fingerprint.getFileSize());
      pstmt.setLong(4, fingerprint.getLastModified().toEpochMilli());
      pstmt.setLong(5, fingerprint.getFirstSeen().toEpochMilli());
      pstmt.setLong(6, fingerprint.getLastSeen().toEpochMilli());
      pstmt.setInt(7, fingerprint.getScanCount());
      pstmt.setString(8, fingerprint.getMetadata());

      pstmt.executeUpdate();

      try (ResultSet rs = pstmt.getGeneratedKeys()) {
        if (rs.next()) {
          long id = rs.getLong(1);
          log.debug("Inserted fingerprint: id={}, path={}", id, fingerprint.getFilePath());
          return id;
        }
      }
    }

    throw new SQLException("Failed to retrieve generated ID for fingerprint");
  }

  /** Finds a fingerprint by file path. */
  public Optional<FileFingerprintEntity> findByPath(String filePath) throws SQLException {
    String sql = "SELECT * FROM file_fingerprints WHERE file_path = ?";

    try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
      pstmt.setString(1, filePath);

      try (ResultSet rs = pstmt.executeQuery()) {
        if (rs.next()) {
          return Optional.of(mapRow(rs));
        }
      }
    }

    return Optional.empty();
  }

  /** Updates an existing fingerprint's hash, size, and timestamps. */
  public void update(String filePath, String fileHash, long fileSize, Instant lastModified)
      throws SQLException {
    String sql =
        """
                UPDATE file_fingerprints
                SET file_hash = ?,
                    file_size = ?,
                    last_modified = ?,
                    last_seen = ?,
                    scan_count = scan_count + 1
                WHERE file_path = ?
                """;

    try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
      pstmt.setString(1, fileHash);
      pstmt.setLong(2, fileSize);
      pstmt.setLong(3, lastModified.toEpochMilli());
      pstmt.setLong(4, Instant.now().toEpochMilli());
      pstmt.setString(5, filePath);

      int updated = pstmt.executeUpdate();
      if (updated > 0) {
        log.debug("Updated fingerprint: path={}", filePath);
      }
    }
  }

  /** Upserts a fingerprint: inserts if new, updates if exists. */
  public void upsert(String filePath, String fileHash, long fileSize, Instant lastModified)
      throws SQLException {
    Optional<FileFingerprintEntity> existing = findByPath(filePath);

    if (existing.isPresent()) {
      update(filePath, fileHash, fileSize, lastModified);
    } else {
      Instant now = Instant.now();
      FileFingerprintEntity fingerprint =
          FileFingerprintEntity.builder()
              .filePath(filePath)
              .fileHash(fileHash)
              .fileSize(fileSize)
              .lastModified(lastModified)
              .firstSeen(now)
              .lastSeen(now)
              .scanCount(1)
              .build();
      insert(fingerprint);
    }
  }

  /** Deletes a fingerprint by file path. */
  public void delete(String filePath) throws SQLException {
    String sql = "DELETE FROM file_fingerprints WHERE file_path = ?";

    try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
      pstmt.setString(1, filePath);
      int deleted = pstmt.executeUpdate();
      if (deleted > 0) {
        log.debug("Deleted fingerprint: path={}", filePath);
      }
    }
  }

  /** Counts total fingerprints. */
  public long count() throws SQLException {
    String sql = "SELECT COUNT(*) FROM file_fingerprints";

    try (PreparedStatement pstmt = connection.prepareStatement(sql);
        ResultSet rs = pstmt.executeQuery()) {
      if (rs.next()) {
        return rs.getLong(1);
      }
    }

    return 0;
  }

  // -------------------------------------------------------------------------
  // Private helpers
  // -------------------------------------------------------------------------

  private FileFingerprintEntity mapRow(ResultSet rs) throws SQLException {
    long lastModifiedMillis = rs.getLong("last_modified");
    Instant lastModified = Instant.ofEpochMilli(lastModifiedMillis);

    long firstSeenMillis = rs.getLong("first_seen");
    Instant firstSeen = Instant.ofEpochMilli(firstSeenMillis);

    long lastSeenMillis = rs.getLong("last_seen");
    Instant lastSeen = Instant.ofEpochMilli(lastSeenMillis);

    return FileFingerprintEntity.builder()
        .id(rs.getLong("id"))
        .filePath(rs.getString("file_path"))
        .fileHash(rs.getString("file_hash"))
        .fileSize(rs.getLong("file_size"))
        .lastModified(lastModified)
        .firstSeen(firstSeen)
        .lastSeen(lastSeen)
        .scanCount(rs.getInt("scan_count"))
        .metadata(rs.getString("metadata"))
        .build();
  }
}
