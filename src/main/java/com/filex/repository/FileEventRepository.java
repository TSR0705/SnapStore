package com.filex.repository;

import com.filex.model.FileEventEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link FileEventEntity} persistence operations.
 *
 * <p>All SQL is encapsulated here. Uses PreparedStatement exclusively
 * for query safety. Supports pagination for large result sets.
 */
public final class FileEventRepository {

    private static final Logger log = LoggerFactory.getLogger(FileEventRepository.class);

    private final Connection connection;

    public FileEventRepository(Connection connection) {
        this.connection = connection;
    }

    /**
     * Inserts a new file event.
     *
     * @return the generated ID
     */
    public long insert(FileEventEntity event) throws SQLException {
        String sql = """
                INSERT INTO file_events (
                    event_id, timestamp, event_type, file_path, file_size, file_hash,
                    process_name, process_id, user_name, suspicious, risk_score, metadata
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, event.getEventId());
            pstmt.setLong(2, event.getTimestamp().toEpochMilli());
            pstmt.setString(3, event.getEventType());
            pstmt.setString(4, event.getFilePath());
            pstmt.setObject(5, event.getFileSize());
            pstmt.setString(6, event.getFileHash());
            pstmt.setString(7, event.getProcessName());
            pstmt.setObject(8, event.getProcessId());
            pstmt.setString(9, event.getUserName());
            pstmt.setInt(10, event.isSuspicious() ? 1 : 0);
            pstmt.setObject(11, event.getRiskScore());
            pstmt.setString(12, event.getMetadata());

            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    log.debug("Inserted file event: id={}, eventId={}", id, event.getEventId());
                    return id;
                }
            }
        }

        throw new SQLException("Failed to retrieve generated ID for file event");
    }

    /**
     * Finds a file event by its unique event ID.
     */
    public Optional<FileEventEntity> findByEventId(String eventId) throws SQLException {
        String sql = "SELECT * FROM file_events WHERE event_id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, eventId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Finds all file events, paginated and ordered by timestamp descending.
     */
    public Page<FileEventEntity> findAll(PageRequest pageRequest) throws SQLException {
        List<FileEventEntity> content = new ArrayList<>();

        String sql = "SELECT * FROM file_events ORDER BY timestamp DESC LIMIT ? OFFSET ?";

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

    /**
     * Finds suspicious file events, paginated.
     */
    public Page<FileEventEntity> findSuspicious(PageRequest pageRequest) throws SQLException {
        List<FileEventEntity> content = new ArrayList<>();

        String sql = "SELECT * FROM file_events WHERE suspicious = 1 ORDER BY timestamp DESC LIMIT ? OFFSET ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, pageRequest.limit());
            pstmt.setInt(2, pageRequest.offset());

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    content.add(mapRow(rs));
                }
            }
        }

        long total = countSuspicious();
        return new Page<>(content, pageRequest.page(), pageRequest.size(), total);
    }

    /**
     * Counts total file events.
     */
    public long count() throws SQLException {
        String sql = "SELECT COUNT(*) FROM file_events";

        try (PreparedStatement pstmt = connection.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }

        return 0;
    }

    /**
     * Counts suspicious file events.
     */
    public long countSuspicious() throws SQLException {
        String sql = "SELECT COUNT(*) FROM file_events WHERE suspicious = 1";

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

    private FileEventEntity mapRow(ResultSet rs) throws SQLException {
        // Handle nullable Long
        Long fileSize = null;
        long fileSizeValue = rs.getLong("file_size");
        if (!rs.wasNull()) {
            fileSize = fileSizeValue;
        }

        // Handle nullable Integer
        Integer processId = null;
        int processIdValue = rs.getInt("process_id");
        if (!rs.wasNull()) {
            processId = processIdValue;
        }

        // Handle nullable Double
        Double riskScore = null;
        double riskScoreValue = rs.getDouble("risk_score");
        if (!rs.wasNull()) {
            riskScore = riskScoreValue;
        }

        // Parse timestamp from epoch milliseconds
        long timestampMillis = rs.getLong("timestamp");
        Instant timestamp = Instant.ofEpochMilli(timestampMillis);

        // Parse created_at from epoch milliseconds
        long createdAtMillis = rs.getLong("created_at");
        Instant createdAt = Instant.ofEpochMilli(createdAtMillis);

        return FileEventEntity.builder()
                .id(rs.getLong("id"))
                .eventId(rs.getString("event_id"))
                .timestamp(timestamp)
                .eventType(rs.getString("event_type"))
                .filePath(rs.getString("file_path"))
                .fileSize(fileSize)
                .fileHash(rs.getString("file_hash"))
                .processName(rs.getString("process_name"))
                .processId(processId)
                .userName(rs.getString("user_name"))
                .suspicious(rs.getInt("suspicious") == 1)
                .riskScore(riskScore)
                .metadata(rs.getString("metadata"))
                .createdAt(createdAt)
                .build();
    }
}
