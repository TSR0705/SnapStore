package com.filex.repository;

import com.filex.model.ForensicTimelineEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link ForensicTimelineEntity} persistence operations.
 *
 * <p>Manages immutable timeline records for chronological reconstruction of
 * security events. Timeline records are ordered by timestamp and sequence number
 * for deterministic replay.
 *
 * <p>All SQL is encapsulated here with PreparedStatement for safety.
 *
 * <p>Thread-safety: Repository instances are NOT thread-safe. Callers must
 * ensure proper synchronization or use separate instances per thread.
 */
public final class ForensicTimelineRepository {

    private static final Logger log = LoggerFactory.getLogger(ForensicTimelineRepository.class);

    private final Connection connection;

    public ForensicTimelineRepository(Connection connection) {
        this.connection = connection;
    }

    /**
     * Inserts a new timeline record.
     *
     * @return the generated database ID
     * @throws SQLException if insert fails
     */
    public long insert(ForensicTimelineEntity timeline) throws SQLException {
        String sql = """
                INSERT INTO forensic_timeline (
                    timeline_id, timestamp, sequence_number, event_type, incident_id,
                    evidence_id, detection_id, severity, description, correlation_id, metadata
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, timeline.getTimelineId());
            pstmt.setLong(2, timeline.getTimestamp().toEpochMilli());
            pstmt.setLong(3, timeline.getSequenceNumber());
            pstmt.setString(4, timeline.getEventType());
            pstmt.setString(5, timeline.getIncidentId());
            pstmt.setString(6, timeline.getEvidenceId());
            pstmt.setString(7, timeline.getDetectionId());
            pstmt.setString(8, timeline.getSeverity());
            pstmt.setString(9, timeline.getDescription());
            pstmt.setString(10, timeline.getCorrelationId());
            pstmt.setString(11, timeline.getMetadata());

            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    log.debug("Inserted timeline record: id={}, timelineId={}, eventType={}", 
                            id, timeline.getTimelineId(), timeline.getEventType());
                    return id;
                }
            }
        }

        throw new SQLException("Failed to retrieve generated ID for timeline record");
    }

    /**
     * Finds a timeline record by its unique timeline ID.
     */
    public Optional<ForensicTimelineEntity> findByTimelineId(String timelineId) throws SQLException {
        String sql = "SELECT * FROM forensic_timeline WHERE timeline_id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, timelineId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Finds timeline records for a specific incident, ordered chronologically.
     */
    public List<ForensicTimelineEntity> findByIncidentId(String incidentId) throws SQLException {
        List<ForensicTimelineEntity> timeline = new ArrayList<>();
        String sql = "SELECT * FROM forensic_timeline WHERE incident_id = ? ORDER BY timestamp ASC, sequence_number ASC";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, incidentId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    timeline.add(mapRow(rs));
                }
            }
        }

        return timeline;
    }

    /**
     * Finds timeline records forward from a specific checkpoint.
     */
    public List<ForensicTimelineEntity> findForwardFromCheckpoint(String incidentId, Instant timestamp, long sequenceNumber, int limit) throws SQLException {
        List<ForensicTimelineEntity> timeline = new ArrayList<>();
        String sql = """
                SELECT * FROM forensic_timeline 
                WHERE incident_id = ? 
                  AND (timestamp > ? OR (timestamp = ? AND sequence_number > ?))
                ORDER BY timestamp ASC, sequence_number ASC 
                LIMIT ?
                """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, incidentId);
            pstmt.setLong(2, timestamp.toEpochMilli());
            pstmt.setLong(3, timestamp.toEpochMilli());
            pstmt.setLong(4, sequenceNumber);
            pstmt.setInt(5, limit);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    timeline.add(mapRow(rs));
                }
            }
        }
        return timeline;
    }

    /**
     * Finds timeline records backward from a specific checkpoint.
     */
    public List<ForensicTimelineEntity> findBackwardFromCheckpoint(String incidentId, Instant timestamp, long sequenceNumber, int limit) throws SQLException {
        List<ForensicTimelineEntity> timeline = new ArrayList<>();
        String sql = """
                SELECT * FROM forensic_timeline 
                WHERE incident_id = ? 
                  AND (timestamp < ? OR (timestamp = ? AND sequence_number < ?))
                ORDER BY timestamp DESC, sequence_number DESC 
                LIMIT ?
                """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, incidentId);
            pstmt.setLong(2, timestamp.toEpochMilli());
            pstmt.setLong(3, timestamp.toEpochMilli());
            pstmt.setLong(4, sequenceNumber);
            pstmt.setInt(5, limit);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    // For backward fetch we get results in DESC order, but we want them displayed chronologically
                    // so we insert at beginning.
                    timeline.add(0, mapRow(rs));
                }
            }
        }
        return timeline;
    }

    /**
     * Counts the total timeline records for a specific incident.
     */
    public long countByIncidentId(String incidentId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM forensic_timeline WHERE incident_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, incidentId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return 0;
    }

    /**
     * Finds timeline records by event type, paginated.
     */
    public Page<ForensicTimelineEntity> findByEventType(String eventType, PageRequest pageRequest) throws SQLException {
        List<ForensicTimelineEntity> content = new ArrayList<>();
        String sql = "SELECT * FROM forensic_timeline WHERE event_type = ? ORDER BY timestamp ASC, sequence_number ASC LIMIT ? OFFSET ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, eventType);
            pstmt.setInt(2, pageRequest.limit());
            pstmt.setInt(3, pageRequest.offset());

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    content.add(mapRow(rs));
                }
            }
        }

        long total = countByEventType(eventType);
        return new Page<>(content, pageRequest.page(), pageRequest.size(), total);
    }

    /**
     * Finds timeline records by severity, paginated.
     */
    public Page<ForensicTimelineEntity> findBySeverity(String severity, PageRequest pageRequest) throws SQLException {
        List<ForensicTimelineEntity> content = new ArrayList<>();
        String sql = "SELECT * FROM forensic_timeline WHERE severity = ? ORDER BY timestamp ASC, sequence_number ASC LIMIT ? OFFSET ?";

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

    /**
     * Finds timeline records by correlation ID, ordered chronologically.
     */
    public List<ForensicTimelineEntity> findByCorrelationId(String correlationId) throws SQLException {
        List<ForensicTimelineEntity> timeline = new ArrayList<>();
        String sql = "SELECT * FROM forensic_timeline WHERE correlation_id = ? ORDER BY timestamp ASC, sequence_number ASC";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, correlationId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    timeline.add(mapRow(rs));
                }
            }
        }

        return timeline;
    }

    /**
     * Finds timeline records within a time range, paginated and ordered chronologically.
     */
    public Page<ForensicTimelineEntity> findByTimeRange(Instant startTime, Instant endTime, PageRequest pageRequest) throws SQLException {
        List<ForensicTimelineEntity> content = new ArrayList<>();
        String sql = "SELECT * FROM forensic_timeline WHERE timestamp BETWEEN ? AND ? ORDER BY timestamp ASC, sequence_number ASC LIMIT ? OFFSET ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, startTime.toEpochMilli());
            pstmt.setLong(2, endTime.toEpochMilli());
            pstmt.setInt(3, pageRequest.limit());
            pstmt.setInt(4, pageRequest.offset());

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    content.add(mapRow(rs));
                }
            }
        }

        long total = countByTimeRange(startTime, endTime);
        return new Page<>(content, pageRequest.page(), pageRequest.size(), total);
    }

    /**
     * Finds all timeline records, paginated and ordered chronologically (ascending for forward replay).
     */
    public Page<ForensicTimelineEntity> findAll(PageRequest pageRequest) throws SQLException {
        List<ForensicTimelineEntity> content = new ArrayList<>();
        String sql = "SELECT * FROM forensic_timeline ORDER BY timestamp ASC, sequence_number ASC LIMIT ? OFFSET ?";

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
     * Gets the next sequence number for a given timestamp.
     * Used to ensure deterministic ordering for events with the same timestamp.
     */
    public long getNextSequenceNumber(Instant timestamp) throws SQLException {
        String sql = "SELECT COALESCE(MAX(sequence_number), 0) + 1 FROM forensic_timeline WHERE timestamp = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, timestamp.toEpochMilli());

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }

        return 1;
    }

    /**
     * Counts timeline records by event type.
     */
    public long countByEventType(String eventType) throws SQLException {
        String sql = "SELECT COUNT(*) FROM forensic_timeline WHERE event_type = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, eventType);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }

        return 0;
    }

    /**
     * Counts timeline records by severity.
     */
    public long countBySeverity(String severity) throws SQLException {
        String sql = "SELECT COUNT(*) FROM forensic_timeline WHERE severity = ?";

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

    /**
     * Counts timeline records within a time range.
     */
    public long countByTimeRange(Instant startTime, Instant endTime) throws SQLException {
        String sql = "SELECT COUNT(*) FROM forensic_timeline WHERE timestamp BETWEEN ? AND ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, startTime.toEpochMilli());
            pstmt.setLong(2, endTime.toEpochMilli());

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }

        return 0;
    }

    /**
     * Counts total timeline records.
     */
    public long count() throws SQLException {
        String sql = "SELECT COUNT(*) FROM forensic_timeline";

        try (PreparedStatement pstmt = connection.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }

        return 0;
    }

    /**
     * Deletes timeline records older than the specified retention period.
     *
     * @param olderThan delete timeline records created before this time
     * @return number of records deleted
     */
    public int deleteOlderThan(Instant olderThan) throws SQLException {
        String sql = "DELETE FROM forensic_timeline WHERE created_at < ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, olderThan.toEpochMilli());
            int deleted = pstmt.executeUpdate();
            
            if (deleted > 0) {
                log.info("Deleted {} old timeline records (older than {})", deleted, olderThan);
            }
            
            return deleted;
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private ForensicTimelineEntity mapRow(ResultSet rs) throws SQLException {
        return ForensicTimelineEntity.builder()
                .id(rs.getLong("id"))
                .timelineId(rs.getString("timeline_id"))
                .timestamp(Instant.ofEpochMilli(rs.getLong("timestamp")))
                .sequenceNumber(rs.getLong("sequence_number"))
                .eventType(rs.getString("event_type"))
                .incidentId(rs.getString("incident_id"))
                .evidenceId(rs.getString("evidence_id"))
                .detectionId(rs.getString("detection_id"))
                .severity(rs.getString("severity"))
                .description(rs.getString("description"))
                .correlationId(rs.getString("correlation_id"))
                .metadata(rs.getString("metadata"))
                .createdAt(Instant.ofEpochMilli(rs.getLong("created_at")))
                .build();
    }
}
