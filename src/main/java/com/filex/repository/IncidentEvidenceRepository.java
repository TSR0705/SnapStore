package com.filex.repository;

import com.filex.model.IncidentEvidenceEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link IncidentEvidenceEntity} persistence operations.
 *
 * <p>Manages immutable evidence records that link detections to incidents.
 * Evidence records are append-only and never modified, preserving forensic integrity.
 *
 * <p>All SQL is encapsulated here with PreparedStatement for safety.
 *
 * <p>Thread-safety: Repository instances are NOT thread-safe. Callers must
 * ensure proper synchronization or use separate instances per thread.
 */
public final class IncidentEvidenceRepository {

    private static final Logger log = LoggerFactory.getLogger(IncidentEvidenceRepository.class);

    private final Connection connection;

    public IncidentEvidenceRepository(Connection connection) {
        this.connection = connection;
    }

    /**
     * Inserts a new evidence record.
     *
     * @return the generated database ID
     * @throws SQLException if insert fails
     */
    public long insert(IncidentEvidenceEntity evidence) throws SQLException {
        String sql = """
                INSERT INTO incident_evidence (
                    evidence_id, incident_id, detection_id, rule_name, severity, confidence,
                    file_path, detected_at, correlation_id, metadata
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, evidence.getEvidenceId());
            pstmt.setString(2, evidence.getIncidentId());
            pstmt.setString(3, evidence.getDetectionId());
            pstmt.setString(4, evidence.getRuleName());
            pstmt.setString(5, evidence.getSeverity());
            pstmt.setString(6, evidence.getConfidence());
            pstmt.setString(7, evidence.getFilePath());
            pstmt.setLong(8, evidence.getDetectedAt().toEpochMilli());
            pstmt.setString(9, evidence.getCorrelationId());
            pstmt.setString(10, evidence.getMetadata());

            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    log.debug("Inserted evidence: id={}, evidenceId={}, incidentId={}", 
                            id, evidence.getEvidenceId(), evidence.getIncidentId());
                    return id;
                }
            }
        }

        throw new SQLException("Failed to retrieve generated ID for evidence");
    }

    /**
     * Finds an evidence record by its unique evidence ID.
     */
    public Optional<IncidentEvidenceEntity> findByEvidenceId(String evidenceId) throws SQLException {
        String sql = "SELECT * FROM incident_evidence WHERE evidence_id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, evidenceId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Finds all evidence records for a specific incident.
     */
    public List<IncidentEvidenceEntity> findByIncidentId(String incidentId) throws SQLException {
        List<IncidentEvidenceEntity> evidence = new ArrayList<>();
        String sql = "SELECT * FROM incident_evidence WHERE incident_id = ? ORDER BY detected_at ASC";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, incidentId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    evidence.add(mapRow(rs));
                }
            }
        }

        return evidence;
    }

    /**
     * Finds evidence by detection ID.
     */
    public Optional<IncidentEvidenceEntity> findByDetectionId(String detectionId) throws SQLException {
        String sql = "SELECT * FROM incident_evidence WHERE detection_id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, detectionId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Finds evidence by rule name, paginated.
     */
    public Page<IncidentEvidenceEntity> findByRuleName(String ruleName, PageRequest pageRequest) throws SQLException {
        List<IncidentEvidenceEntity> content = new ArrayList<>();
        String sql = "SELECT * FROM incident_evidence WHERE rule_name = ? ORDER BY detected_at DESC LIMIT ? OFFSET ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, ruleName);
            pstmt.setInt(2, pageRequest.limit());
            pstmt.setInt(3, pageRequest.offset());

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    content.add(mapRow(rs));
                }
            }
        }

        long total = countByRuleName(ruleName);
        return new Page<>(content, pageRequest.page(), pageRequest.size(), total);
    }

    /**
     * Finds evidence by file path, paginated.
     */
    public Page<IncidentEvidenceEntity> findByFilePath(String filePath, PageRequest pageRequest) throws SQLException {
        List<IncidentEvidenceEntity> content = new ArrayList<>();
        String sql = "SELECT * FROM incident_evidence WHERE file_path = ? ORDER BY detected_at DESC LIMIT ? OFFSET ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, filePath);
            pstmt.setInt(2, pageRequest.limit());
            pstmt.setInt(3, pageRequest.offset());

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    content.add(mapRow(rs));
                }
            }
        }

        long total = countByFilePath(filePath);
        return new Page<>(content, pageRequest.page(), pageRequest.size(), total);
    }

    /**
     * Finds evidence by correlation ID.
     */
    public List<IncidentEvidenceEntity> findByCorrelationId(String correlationId) throws SQLException {
        List<IncidentEvidenceEntity> evidence = new ArrayList<>();
        String sql = "SELECT * FROM incident_evidence WHERE correlation_id = ? ORDER BY detected_at ASC";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, correlationId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    evidence.add(mapRow(rs));
                }
            }
        }

        return evidence;
    }

    /**
     * Counts evidence records for an incident.
     */
    public long countByIncidentId(String incidentId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM incident_evidence WHERE incident_id = ?";

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
     * Counts evidence by rule name.
     */
    public long countByRuleName(String ruleName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM incident_evidence WHERE rule_name = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, ruleName);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }

        return 0;
    }

    /**
     * Counts evidence by file path.
     */
    public long countByFilePath(String filePath) throws SQLException {
        String sql = "SELECT COUNT(*) FROM incident_evidence WHERE file_path = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, filePath);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }

        return 0;
    }

    /**
     * Counts total evidence records.
     */
    public long count() throws SQLException {
        String sql = "SELECT COUNT(*) FROM incident_evidence";

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

    private IncidentEvidenceEntity mapRow(ResultSet rs) throws SQLException {
        return IncidentEvidenceEntity.builder()
                .id(rs.getLong("id"))
                .evidenceId(rs.getString("evidence_id"))
                .incidentId(rs.getString("incident_id"))
                .detectionId(rs.getString("detection_id"))
                .ruleName(rs.getString("rule_name"))
                .severity(rs.getString("severity"))
                .confidence(rs.getString("confidence"))
                .filePath(rs.getString("file_path"))
                .detectedAt(Instant.ofEpochMilli(rs.getLong("detected_at")))
                .correlationId(rs.getString("correlation_id"))
                .metadata(rs.getString("metadata"))
                .createdAt(Instant.ofEpochMilli(rs.getLong("created_at")))
                .build();
    }
}
