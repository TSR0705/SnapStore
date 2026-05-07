package com.filex.alert;

import com.filex.detection.Confidence;

import java.time.Instant;
import java.util.*;

/**
 * Immutable incident model representing a correlated security event.
 *
 * <p>An incident aggregates one or more related detections into a single
 * actionable security event. Incidents track:
 * <ul>
 *   <li>Correlation metadata</li>
 *   <li>Evidence chain (linked detections)</li>
 *   <li>Lifecycle status</li>
 *   <li>Severity and confidence</li>
 *   <li>Temporal tracking (created, updated, last seen)</li>
 * </ul>
 *
 * <p>Thread-safety: Immutable after construction.
 */
public final class Incident {

    private final String incidentId;
    private final IncidentSeverity severity;
    private final Confidence confidence;
    private final IncidentStatus status;
    private final String title;
    private final String description;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant lastSeenAt;
    private final List<String> linkedDetectionIds;
    private final String correlationId;
    private final Map<String, Object> evidenceSummary;
    private final int escalationLevel;
    private final int detectionCount;

    private Incident(Builder builder) {
        this.incidentId = Objects.requireNonNull(builder.incidentId, "incidentId must not be null");
        this.severity = Objects.requireNonNull(builder.severity, "severity must not be null");
        this.confidence = Objects.requireNonNull(builder.confidence, "confidence must not be null");
        this.status = Objects.requireNonNull(builder.status, "status must not be null");
        this.title = Objects.requireNonNull(builder.title, "title must not be null");
        this.description = builder.description != null ? builder.description : "";
        this.createdAt = Objects.requireNonNull(builder.createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(builder.updatedAt, "updatedAt must not be null");
        this.lastSeenAt = Objects.requireNonNull(builder.lastSeenAt, "lastSeenAt must not be null");
        this.linkedDetectionIds = List.copyOf(builder.linkedDetectionIds);
        this.correlationId = builder.correlationId;
        this.evidenceSummary = builder.evidenceSummary != null ? 
                Map.copyOf(builder.evidenceSummary) : Map.of();
        this.escalationLevel = builder.escalationLevel;
        this.detectionCount = builder.detectionCount;
    }

    public String getIncidentId() {
        return incidentId;
    }

    public IncidentSeverity getSeverity() {
        return severity;
    }

    public Confidence getConfidence() {
        return confidence;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public List<String> getLinkedDetectionIds() {
        return linkedDetectionIds;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Map<String, Object> getEvidenceSummary() {
        return evidenceSummary;
    }

    public int getEscalationLevel() {
        return escalationLevel;
    }

    public int getDetectionCount() {
        return detectionCount;
    }

    /**
     * Creates a new builder for constructing incidents.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a builder initialized with this incident's values.
     */
    public Builder toBuilder() {
        return new Builder()
                .incidentId(incidentId)
                .severity(severity)
                .confidence(confidence)
                .status(status)
                .title(title)
                .description(description)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .lastSeenAt(lastSeenAt)
                .linkedDetectionIds(linkedDetectionIds)
                .correlationId(correlationId)
                .evidenceSummary(evidenceSummary)
                .escalationLevel(escalationLevel)
                .detectionCount(detectionCount);
    }

    @Override
    public String toString() {
        return "Incident{" +
                "incidentId='" + incidentId + '\'' +
                ", severity=" + severity +
                ", confidence=" + confidence +
                ", status=" + status +
                ", title='" + title + '\'' +
                ", detectionCount=" + detectionCount +
                ", escalationLevel=" + escalationLevel +
                '}';
    }

    /**
     * Builder for constructing Incident instances.
     */
    public static final class Builder {
        private String incidentId;
        private IncidentSeverity severity;
        private Confidence confidence;
        private IncidentStatus status = IncidentStatus.OPEN;
        private String title;
        private String description;
        private Instant createdAt;
        private Instant updatedAt;
        private Instant lastSeenAt;
        private List<String> linkedDetectionIds = new ArrayList<>();
        private String correlationId;
        private Map<String, Object> evidenceSummary;
        private int escalationLevel = 0;
        private int detectionCount = 1;

        public Builder incidentId(String incidentId) {
            this.incidentId = incidentId;
            return this;
        }

        public Builder severity(IncidentSeverity severity) {
            this.severity = severity;
            return this;
        }

        public Builder confidence(Confidence confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder status(IncidentStatus status) {
            this.status = status;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Builder lastSeenAt(Instant lastSeenAt) {
            this.lastSeenAt = lastSeenAt;
            return this;
        }

        public Builder linkedDetectionIds(List<String> linkedDetectionIds) {
            this.linkedDetectionIds = new ArrayList<>(linkedDetectionIds);
            return this;
        }

        public Builder addLinkedDetectionId(String detectionId) {
            this.linkedDetectionIds.add(detectionId);
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder evidenceSummary(Map<String, Object> evidenceSummary) {
            this.evidenceSummary = new HashMap<>(evidenceSummary);
            return this;
        }

        public Builder escalationLevel(int escalationLevel) {
            this.escalationLevel = escalationLevel;
            return this;
        }

        public Builder detectionCount(int detectionCount) {
            this.detectionCount = detectionCount;
            return this;
        }

        public Incident build() {
            return new Incident(this);
        }
    }
}
