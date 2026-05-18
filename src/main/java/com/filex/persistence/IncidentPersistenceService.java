package com.filex.persistence;

import com.filex.alert.Incident;
import com.filex.detection.DetectionEvent;
import com.filex.model.ForensicTimelineEntity;
import com.filex.model.IncidentEntity;
import com.filex.model.IncidentEvidenceEntity;
import com.filex.repository.ForensicTimelineRepository;
import com.filex.repository.IncidentEvidenceRepository;
import com.filex.repository.IncidentRepository;
import com.filex.validation.TruthMarkers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for persisting incidents with transactional consistency.
 *
 * <p>Orchestrates atomic writes across:
 * <ul>
 *   <li>Incident records</li>
 *   <li>Evidence records</li>
 *   <li>Timeline records</li>
 * </ul>
 *
 * <p>All persistence operations are transaction-safe. Failures trigger rollback
 * to prevent partial writes and forensic corruption.
 *
 * <p>Thread-safety: This service is thread-safe. Multiple threads can call
 * persistence methods concurrently.
 */
public final class IncidentPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(IncidentPersistenceService.class);

    private final Connection connection;
    private final IncidentRepository incidentRepository;
    private final IncidentEvidenceRepository evidenceRepository;
    private final ForensicTimelineRepository timelineRepository;
    private final TransactionTemplate transactionTemplate;

    // Metrics
    private final AtomicLong totalIncidentsPersisted = new AtomicLong(0);
    private final AtomicLong totalEvidencePersisted = new AtomicLong(0);
    private final AtomicLong totalTimelineRecords = new AtomicLong(0);
    private final AtomicLong totalPersistenceFailures = new AtomicLong(0);
    private final AtomicLong totalTransactionRollbacks = new AtomicLong(0);

    public IncidentPersistenceService(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection must not be null");
        this.incidentRepository = new IncidentRepository(connection);
        this.evidenceRepository = new IncidentEvidenceRepository(connection);
        this.timelineRepository = new ForensicTimelineRepository(connection);
        this.transactionTemplate = new TransactionTemplate(connection);
    }

    /**
     * Persists a new incident with its initial detection evidence.
     *
     * <p>Atomically writes:
     * <ol>
     *   <li>Incident record</li>
     *   <li>Evidence record linking the detection</li>
     *   <li>Timeline record for incident creation</li>
     * </ol>
     *
     * @param incident the incident to persist
     * @param detection the detection that triggered the incident
     * @throws PersistenceException if persistence fails
     */
    public void persistNewIncident(Incident incident, DetectionEvent detection) throws PersistenceException {
        Objects.requireNonNull(incident, "incident must not be null");
        Objects.requireNonNull(detection, "detection must not be null");

        final String op = "persistNewIncident";
        final long startMs = System.currentTimeMillis();
        log.debug(TruthMarkers.TRUTH,
                "component=IncidentPersistenceService event=tx_begin op={} incidentId={}",
                op, incident.getIncidentId());

        try {
            transactionTemplate.executeVoid(conn -> {
                // 1. Persist incident
                IncidentEntity incidentEntity = mapToEntity(incident);
                long incidentStartMs = System.currentTimeMillis();
                log.debug(TruthMarkers.TRUTH,
                        "component=IncidentPersistenceService event=incident_write_attempt incidentId={}",
                        incident.getIncidentId());
                try {
                    incidentRepository.insert(incidentEntity);
                    log.debug(TruthMarkers.TRUTH,
                            "component=IncidentPersistenceService event=incident_write_result incidentId={} outcome=ok durationMs={}",
                            incident.getIncidentId(), System.currentTimeMillis() - incidentStartMs);
                } catch (RuntimeException | SQLException ex) {
                    log.error(TruthMarkers.TRUTH,
                            "component=IncidentPersistenceService event=incident_write_result incidentId={} outcome=fail durationMs={}",
                            incident.getIncidentId(), System.currentTimeMillis() - incidentStartMs);
                    throw ex;
                }

                // 2. Persist evidence
                IncidentEvidenceEntity evidenceEntity = createEvidenceEntity(incident, detection);
                log.debug(TruthMarkers.TRUTH,
                        "component=IncidentPersistenceService event=evidence_write_attempt incidentId={} evidenceId={}",
                        incident.getIncidentId(), evidenceEntity.getEvidenceId());
                try {
                    evidenceRepository.insert(evidenceEntity);
                    log.debug(TruthMarkers.TRUTH,
                            "component=IncidentPersistenceService event=evidence_write_result evidenceId={} outcome=ok",
                            evidenceEntity.getEvidenceId());
                } catch (RuntimeException | SQLException ex) {
                    log.error(TruthMarkers.TRUTH,
                            "component=IncidentPersistenceService event=evidence_write_result evidenceId={} outcome=fail",
                            evidenceEntity.getEvidenceId());
                    throw ex;
                }

                // 3. Persist timeline record
                ForensicTimelineEntity timelineEntity = createIncidentCreatedTimeline(incident, detection);
                try {
                    timelineRepository.insert(timelineEntity);
                    log.debug(TruthMarkers.TRUTH,
                            "component=IncidentPersistenceService event=timeline_write_result timelineId={} seq={} outcome=ok",
                            timelineEntity.getTimelineId(), timelineEntity.getSequenceNumber());
                } catch (RuntimeException | SQLException ex) {
                    log.error(TruthMarkers.TRUTH,
                            "component=IncidentPersistenceService event=timeline_write_result timelineId={} seq={} outcome=fail",
                            timelineEntity.getTimelineId(), timelineEntity.getSequenceNumber());
                    throw ex;
                }

                log.info("Persisted new incident: incidentId={}, detectionId={}", 
                        incident.getIncidentId(), detection.eventId());
            });

            totalIncidentsPersisted.incrementAndGet();
            totalEvidencePersisted.incrementAndGet();
            totalTimelineRecords.incrementAndGet();

            log.info(TruthMarkers.TRUTH,
                    "component=IncidentPersistenceService event=tx_commit op={} incidentId={} durationMs={}",
                    op, incident.getIncidentId(), System.currentTimeMillis() - startMs);

        } catch (Exception e) {
            totalPersistenceFailures.incrementAndGet();
            totalTransactionRollbacks.incrementAndGet();
            log.error(TruthMarkers.TRUTH,
                    "component=IncidentPersistenceService event=tx_rollback op={} incidentId={} err={}",
                    op, incident.getIncidentId(), e.getMessage(), e);
            log.error("Failed to persist new incident: incidentId={}", incident.getIncidentId(), e);
            throw new PersistenceException("Failed to persist new incident: " + incident.getIncidentId(), e);
        }
    }

    /**
     * Persists an incident update (merge, escalation, status change).
     *
     * <p>Atomically writes:
     * <ol>
     *   <li>Updated incident record</li>
     *   <li>New evidence record (if detection provided)</li>
     *   <li>Timeline record for the update</li>
     * </ol>
     *
     * @param incident the updated incident
     * @param detection optional detection that triggered the update
     * @param updateReason description of why the incident was updated
     * @throws PersistenceException if persistence fails
     */
    public void persistIncidentUpdate(Incident incident, DetectionEvent detection, String updateReason) 
            throws PersistenceException {
        Objects.requireNonNull(incident, "incident must not be null");
        Objects.requireNonNull(updateReason, "updateReason must not be null");

        final String op = "persistIncidentUpdate";
        final long startMs = System.currentTimeMillis();
        log.debug(TruthMarkers.TRUTH,
                "component=IncidentPersistenceService event=tx_begin op={} incidentId={}",
                op, incident.getIncidentId());

        try {
            transactionTemplate.executeVoid(conn -> {
                // 1. Update incident
                IncidentEntity incidentEntity = mapToEntity(incident);
                long incidentStartMs = System.currentTimeMillis();
                log.debug(TruthMarkers.TRUTH,
                        "component=IncidentPersistenceService event=incident_write_attempt incidentId={}",
                        incident.getIncidentId());
                try {
                    incidentRepository.update(incidentEntity);
                    log.debug(TruthMarkers.TRUTH,
                            "component=IncidentPersistenceService event=incident_write_result incidentId={} outcome=ok durationMs={}",
                            incident.getIncidentId(), System.currentTimeMillis() - incidentStartMs);
                } catch (RuntimeException | SQLException ex) {
                    log.error(TruthMarkers.TRUTH,
                            "component=IncidentPersistenceService event=incident_write_result incidentId={} outcome=fail durationMs={}",
                            incident.getIncidentId(), System.currentTimeMillis() - incidentStartMs);
                    throw ex;
                }

                // 2. Persist evidence if detection provided
                if (detection != null) {
                    IncidentEvidenceEntity evidenceEntity = createEvidenceEntity(incident, detection);
                    log.debug(TruthMarkers.TRUTH,
                            "component=IncidentPersistenceService event=evidence_write_attempt incidentId={} evidenceId={}",
                            incident.getIncidentId(), evidenceEntity.getEvidenceId());
                    try {
                        evidenceRepository.insert(evidenceEntity);
                        log.debug(TruthMarkers.TRUTH,
                                "component=IncidentPersistenceService event=evidence_write_result evidenceId={} outcome=ok",
                                evidenceEntity.getEvidenceId());
                    } catch (RuntimeException | SQLException ex) {
                        log.error(TruthMarkers.TRUTH,
                                "component=IncidentPersistenceService event=evidence_write_result evidenceId={} outcome=fail",
                                evidenceEntity.getEvidenceId());
                        throw ex;
                    }
                    totalEvidencePersisted.incrementAndGet();
                }

                // 3. Persist timeline record
                ForensicTimelineEntity timelineEntity = createIncidentUpdatedTimeline(incident, detection, updateReason);
                try {
                    timelineRepository.insert(timelineEntity);
                    log.debug(TruthMarkers.TRUTH,
                            "component=IncidentPersistenceService event=timeline_write_result timelineId={} seq={} outcome=ok",
                            timelineEntity.getTimelineId(), timelineEntity.getSequenceNumber());
                } catch (RuntimeException | SQLException ex) {
                    log.error(TruthMarkers.TRUTH,
                            "component=IncidentPersistenceService event=timeline_write_result timelineId={} seq={} outcome=fail",
                            timelineEntity.getTimelineId(), timelineEntity.getSequenceNumber());
                    throw ex;
                }

                log.debug("Persisted incident update: incidentId={}, reason={}", 
                        incident.getIncidentId(), updateReason);
            });

            totalTimelineRecords.incrementAndGet();

            log.info(TruthMarkers.TRUTH,
                    "component=IncidentPersistenceService event=tx_commit op={} incidentId={} durationMs={}",
                    op, incident.getIncidentId(), System.currentTimeMillis() - startMs);

        } catch (Exception e) {
            totalPersistenceFailures.incrementAndGet();
            totalTransactionRollbacks.incrementAndGet();
            log.error(TruthMarkers.TRUTH,
                    "component=IncidentPersistenceService event=tx_rollback op={} incidentId={} err={}",
                    op, incident.getIncidentId(), e.getMessage(), e);
            log.error("Failed to persist incident update: incidentId={}", incident.getIncidentId(), e);
            throw new PersistenceException("Failed to persist incident update: " + incident.getIncidentId(), e);
        }
    }

    /**
     * Recovers active incidents from the database.
     *
     * <p>Used during application startup to restore runtime state.
     *
     * @return list of active incidents (OPEN or INVESTIGATING status)
     * @throws PersistenceException if recovery fails
     */
    public List<IncidentEntity> recoverActiveIncidents() throws PersistenceException {
        try {
            List<IncidentEntity> activeIncidents = incidentRepository.findActive();
            log.info("Recovered {} active incidents from database", activeIncidents.size());
            return activeIncidents;
        } catch (SQLException e) {
            log.error("Failed to recover active incidents", e);
            throw new PersistenceException("Failed to recover active incidents", e);
        }
    }

    /**
     * Recovers evidence for a specific incident.
     *
     * @param incidentId the incident ID
     * @return list of evidence records for the incident
     * @throws PersistenceException if recovery fails
     */
    public List<IncidentEvidenceEntity> recoverIncidentEvidence(String incidentId) throws PersistenceException {
        try {
            return evidenceRepository.findByIncidentId(incidentId);
        } catch (SQLException e) {
            log.error("Failed to recover evidence for incident: {}", incidentId, e);
            throw new PersistenceException("Failed to recover evidence for incident: " + incidentId, e);
        }
    }

    /**
     * Finds an incident by ID.
     */
    public Optional<IncidentEntity> findIncidentById(String incidentId) throws PersistenceException {
        try {
            return incidentRepository.findByIncidentId(incidentId);
        } catch (SQLException e) {
            log.error("Failed to find incident: {}", incidentId, e);
            throw new PersistenceException("Failed to find incident: " + incidentId, e);
        }
    }

    /**
     * Returns persistence metrics.
     */
    public PersistenceMetrics getMetrics() {
        return new PersistenceMetrics(
                totalIncidentsPersisted.get(),
                totalEvidencePersisted.get(),
                totalTimelineRecords.get(),
                totalPersistenceFailures.get(),
                totalTransactionRollbacks.get()
        );
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private IncidentEntity mapToEntity(Incident incident) {
        // Convert evidence summary to JSON string if needed
        String metadata = incident.getEvidenceSummary().isEmpty() ? null : 
                incident.getEvidenceSummary().toString();

        return IncidentEntity.builder()
                .incidentId(incident.getIncidentId())
                .severity(incident.getSeverity().name())
                .confidence(incident.getConfidence().name())
                .status(incident.getStatus().name())
                .title(incident.getTitle())
                .description(incident.getDescription())
                .createdAt(incident.getCreatedAt())
                .updatedAt(incident.getUpdatedAt())
                .lastSeenAt(incident.getLastSeenAt())
                .correlationId(incident.getCorrelationId())
                .escalationLevel(incident.getEscalationLevel())
                .detectionCount(incident.getDetectionCount())
                .metadata(metadata)
                .build();
    }

    private IncidentEvidenceEntity createEvidenceEntity(Incident incident, DetectionEvent detection) {
        String evidenceId = UUID.randomUUID().toString();
        String filePath = detection.affectedPaths().isEmpty() ? null : 
                detection.affectedPaths().get(0).toString();

        return IncidentEvidenceEntity.builder()
                .evidenceId(evidenceId)
                .incidentId(incident.getIncidentId())
                .detectionId(detection.eventId())
                .ruleName(detection.ruleName())
                .severity(detection.severity().name())
                .confidence(detection.confidence().name())
                .filePath(filePath)
                .detectedAt(Instant.now())
                .correlationId(incident.getCorrelationId())
                .metadata(detection.description())
                .build();
    }

    private ForensicTimelineEntity createIncidentCreatedTimeline(Incident incident, DetectionEvent detection) {
        String timelineId = UUID.randomUUID().toString();
        Instant timestamp = incident.getCreatedAt();
        
        try {
            long sequenceNumber = timelineRepository.getNextSequenceNumber(timestamp);
            
            return ForensicTimelineEntity.builder()
                    .timelineId(timelineId)
                    .timestamp(timestamp)
                    .sequenceNumber(sequenceNumber)
                    .eventType("INCIDENT_CREATED")
                    .incidentId(incident.getIncidentId())
                    .detectionId(detection.eventId())
                    .severity(incident.getSeverity().name())
                    .description("Incident created: " + incident.getTitle())
                    .correlationId(incident.getCorrelationId())
                    .metadata(detection.ruleName())
                    .build();
        } catch (SQLException e) {
            // Fallback to sequence 1 if query fails
            log.warn("Failed to get next sequence number, using 1", e);
            return ForensicTimelineEntity.builder()
                    .timelineId(timelineId)
                    .timestamp(timestamp)
                    .sequenceNumber(1)
                    .eventType("INCIDENT_CREATED")
                    .incidentId(incident.getIncidentId())
                    .detectionId(detection.eventId())
                    .severity(incident.getSeverity().name())
                    .description("Incident created: " + incident.getTitle())
                    .correlationId(incident.getCorrelationId())
                    .metadata(detection.ruleName())
                    .build();
        }
    }

    private ForensicTimelineEntity createIncidentUpdatedTimeline(Incident incident, DetectionEvent detection, String updateReason) {
        String timelineId = UUID.randomUUID().toString();
        Instant timestamp = incident.getUpdatedAt();
        
        try {
            long sequenceNumber = timelineRepository.getNextSequenceNumber(timestamp);
            
            return ForensicTimelineEntity.builder()
                    .timelineId(timelineId)
                    .timestamp(timestamp)
                    .sequenceNumber(sequenceNumber)
                    .eventType("INCIDENT_UPDATED")
                    .incidentId(incident.getIncidentId())
                    .detectionId(detection != null ? detection.eventId() : null)
                    .severity(incident.getSeverity().name())
                    .description(updateReason)
                    .correlationId(incident.getCorrelationId())
                    .metadata(detection != null ? detection.ruleName() : null)
                    .build();
        } catch (SQLException e) {
            // Fallback to sequence 1 if query fails
            log.warn("Failed to get next sequence number, using 1", e);
            return ForensicTimelineEntity.builder()
                    .timelineId(timelineId)
                    .timestamp(timestamp)
                    .sequenceNumber(1)
                    .eventType("INCIDENT_UPDATED")
                    .incidentId(incident.getIncidentId())
                    .detectionId(detection != null ? detection.eventId() : null)
                    .severity(incident.getSeverity().name())
                    .description(updateReason)
                    .correlationId(incident.getCorrelationId())
                    .metadata(detection != null ? detection.ruleName() : null)
                    .build();
        }
    }

    /**
     * Immutable metrics snapshot for persistence operations.
     */
    public record PersistenceMetrics(
            long incidentsPersisted,
            long evidencePersisted,
            long timelineRecords,
            long persistenceFailures,
            long transactionRollbacks
    ) {}
}
