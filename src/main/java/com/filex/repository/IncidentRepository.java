package com.filex.repository;

import com.filex.model.IncidentEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link IncidentEntity} persistence operations.
 *
 * <p>Manages incident records with full CRUD support. Incidents are mutable
 * (status and severity can escalate), but all changes are tracked via timestamps.
 *
 * <p>All SQL is encapsulated here with PreparedStatement for safety.
 * No SQL logic exists outside this repository.
 *
 * <p>Thread-safety: Repository instances are NOT thread-safe. Callers must
 * ensure proper synchronization or use separate instances per thread.
 */
public final class IncidentRepository {

    private static final Logger log = LoggerFactory.getLogger(IncidentRepository.class);

    private final Connection connection;

    public IncidentRepository(Connection connection) {
        this.connection = connection;
    }

    /**
     * Inserts a new incident.
     *
     * @return the generated database ID
     * @throws SQLException if insert fails
     */
    public long insert(IncidentEntity incident) throws SQLException {
        String sql = """
                INSERT INTO incidents (
                    incident_id, severity, confidence, status, title, description,
                    created_at, updated_at, last_seen_at, correlation_id,
                    escalation_level, detection_count, metadata
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, incident.getIncidentId());
            pstmt.setString(2, incident.getSeverity());
            pstmt.setString(3, incident.getConfidence());
            pstmt.setString(4, incident.getStatus());
            pstmt.setString(5, incident.getTitle());
            pstmt.setString(6, incident.getDescription());
            pstmt.setLong(7, incident.getCreatedAt().toEpochMilli());
            pstmt.setLong(8, incident.getUpdatedAt().toEpochMilli());
            pstmt.setLong(9, incident.getLastSeenAt().toEpochMilli());
            pstmt.setString(10, incident.getCorrelationId());
            pstmt.setInt(11, incident.getEscalationLevel());
            pstmt.setInt(12, incident.getDetectionCount());
            pstmt.setString(13, incident.getMetadata());

            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    log.debug("Inserted incident: id={}, incidentId={}", id, incident.getIncidentId());
                    return id;
                }
            }
        }

        throw new SQLException("Failed to retrieve generated ID for incident");
    }

    /**
     * Updates an existing incident.
     *
     * @throws SQLException if update fails or incident not found
     */
    public void update(IncidentEntity incident) throws SQLException {
        String sql = """
                UPDATE incidents
                SET severity = ?,
                    confidence = ?,
                    status = ?,
                    title = ?,
                    description = ?,
                    updated_at = ?,
                    last_seen_at = ?,
                    correlation_id = ?,
                    escalation_level = ?,
                    detection_count = ?,
                    metadata = ?
                WHERE incident_id = ?
                """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, incident.getSeverity());
            pstmt.setString(2, incident.getConfidence());
            pstmt.setString(3, incident.getStatus());
            pstmt.setString(4, incident.getTitle());
            pstmt.setString(5, incident.getDescription());
            pstmt.setLong(6, incident.getUpdatedAt().toEpochMilli());
            pstmt.setLong(7, incident.getLastSeenAt().toEpochMilli());
            pstmt.setString(8, incident.getCorrelationId());
            pstmt.setInt(9, incident.getEscalationLevel());
            pstmt.setInt(10, incident.getDetectionCount());
            pstmt.setString(11, incident.getMetadata());
            pstmt.setString(12, incident.getIncidentId());

            int updated = pstmt.executeUpdate();
            if (updated == 0) {
                throw new SQLException("Incident not found for update: " + incident.getIncidentId());
            }
            log.debug("Updated incident: incidentId={}", incident.getIncidentId());
        }
    }

    /**
     * Finds an incident by its unique incident ID.
     */
    public Optional<IncidentEntity> findByIncidentId(String incidentId) throws SQLException {
        String sql = "SELECT * FROM incidents WHERE incident_id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, incidentId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Finds all incidents with a specific status.
     */
    public List<IncidentEntity> findByStatus(String status) throws SQLException {
        List<IncidentEntity> incidents = new ArrayList<>();
        String sql = "SELECT * FROM incidents WHERE status = ? ORDER BY created_at DESC";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, status);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    incidents.add(mapRow(rs));
                }
            }
        }

        return incidents;
    }

    /**
     * Finds all active incidents (status = OPEN or INVESTIGATING).
     */
    public List<IncidentEntity> findActive() throws SQLException {
        List<IncidentEntity> incidents = new ArrayList<>();
        String sql = "SELECT * FROM incidents WHERE status IN ('OPEN', 'INVESTIGATING') ORDER BY severity DESC, created_at DESC";

        try (PreparedStatement pstmt = connection.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                incidents.add(mapRow(rs));
            }
        }

        return incidents;
    }

    /**
     * Finds incidents by severity, paginated.
     */
    public Page<IncidentEntity> findBySeverity(String severity, PageRequest pageRequest) throws SQLException {
        List<IncidentEntity> content = new ArrayList<>();
        String sql = "SELECT * FROM incidents WHERE severity = ? ORDER BY created_at DESC LIMIT ? OFFSET ?";

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
     * Finds incidents by correlation ID.
     */
    public List<IncidentEntity> findByCorrelationId(String correlationId) throws SQLException {
        List<IncidentEntity> incidents = new ArrayList<>();
        String sql = "SELECT * FROM incidents WHERE correlation_id = ? ORDER BY created_at DESC";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, correlationId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    incidents.add(mapRow(rs));
                }
            }
        }

        return incidents;
    }

    /**
     * Finds incidents within a time range, paginated.
     */
    public Page<IncidentEntity> findByTimeRange(Instant startTime, Instant endTime, PageRequest pageRequest) throws SQLException {
        List<IncidentEntity> content = new ArrayList<>();
        String sql = "SELECT * FROM incidents WHERE created_at BETWEEN ? AND ? ORDER BY created_at DESC LIMIT ? OFFSET ?";

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
     * Finds all incidents, paginated and ordered by creation time descending.
     */
    public Page<IncidentEntity> findAll(PageRequest pageRequest) throws SQLException {
        List<IncidentEntity> content = new ArrayList<>();
        String sql = "SELECT * FROM incidents ORDER BY created_at DESC LIMIT ? OFFSET ?";

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
     * Counts total incidents.
     */
    public long count() throws SQLException {
        String sql = "SELECT COUNT(*) FROM incidents";

        try (PreparedStatement pstmt = connection.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }

        return 0;
    }

    /**
     * Counts incidents by severity.
     */
    public long countBySeverity(String severity) throws SQLException {
        String sql = "SELECT COUNT(*) FROM incidents WHERE severity = ?";

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
     * Counts incidents by status.
     */
    public long countByStatus(String status) throws SQLException {
        String sql = "SELECT COUNT(*) FROM incidents WHERE status = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, status);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }

        return 0;
    }

    /**
     * Counts incidents within a time range.
     */
    public long countByTimeRange(Instant startTime, Instant endTime) throws SQLException {
        String sql = "SELECT COUNT(*) FROM incidents WHERE created_at BETWEEN ? AND ?";

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
     * Deletes incidents older than the specified retention period.
     * Only deletes resolved or dismissed incidents.
     *
     * @param olderThan delete incidents created before this time
     * @return number of incidents deleted
     */
    public int deleteOlderThan(Instant olderThan) throws SQLException {
        String sql = "DELETE FROM incidents WHERE created_at < ? AND status IN ('RESOLVED', 'DISMISSED')";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, olderThan.toEpochMilli());
            int deleted = pstmt.executeUpdate();
            
            if (deleted > 0) {
                log.info("Deleted {} old incidents (older than {})", deleted, olderThan);
            }
            
            return deleted;
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private IncidentEntity mapRow(ResultSet rs) throws SQLException {
        return IncidentEntity.builder()
                .id(rs.getLong("id"))
                .incidentId(rs.getString("incident_id"))
                .severity(rs.getString("severity"))
                .confidence(rs.getString("confidence"))
                .status(rs.getString("status"))
                .title(rs.getString("title"))
                .description(rs.getString("description"))
                .createdAt(Instant.ofEpochMilli(rs.getLong("created_at")))
                .updatedAt(Instant.ofEpochMilli(rs.getLong("updated_at")))
                .lastSeenAt(Instant.ofEpochMilli(rs.getLong("last_seen_at")))
                .correlationId(rs.getString("correlation_id"))
                .escalationLevel(rs.getInt("escalation_level"))
                .detectionCount(rs.getInt("detection_count"))
                .metadata(rs.getString("metadata"))
                .build();
    }
}
