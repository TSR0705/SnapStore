package com.filex.investigation;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable summary of evidence for investigation purposes.
 *
 * <p>Provides a lightweight view of evidence data optimized for
 * evidence chain exploration and forensic analysis.
 *
 * <p>Thread-safety: Immutable after construction.
 */
public final class EvidenceSummary {

    private final String evidenceId;
    private final String incidentId;
    private final String detectionId;
    private final String ruleName;
    private final String severity;
    private final String confidence;
    private final String filePath;
    private final Instant detectedAt;
    private final String correlationId;

    private EvidenceSummary(Builder builder) {
        this.evidenceId = Objects.requireNonNull(builder.evidenceId, "evidenceId must not be null");
        this.incidentId = Objects.requireNonNull(builder.incidentId, "incidentId must not be null");
        this.detectionId = Objects.requireNonNull(builder.detectionId, "detectionId must not be null");
        this.ruleName = Objects.requireNonNull(builder.ruleName, "ruleName must not be null");
        this.severity = Objects.requireNonNull(builder.severity, "severity must not be null");
        this.confidence = Objects.requireNonNull(builder.confidence, "confidence must not be null");
        this.filePath = builder.filePath;
        this.detectedAt = Objects.requireNonNull(builder.detectedAt, "detectedAt must not be null");
        this.correlationId = builder.correlationId;
    }

    // Getters
    public String getEvidenceId() { return evidenceId; }
    public String getIncidentId() { return incidentId; }
    public String getDetectionId() { return detectionId; }
    public String getRuleName() { return ruleName; }
    public String getSeverity() { return severity; }
    public String getConfidence() { return confidence; }
    public String getFilePath() { return filePath; }
    public Instant getDetectedAt() { return detectedAt; }
    public String getCorrelationId() { return correlationId; }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "EvidenceSummary{" +
                "evidenceId='" + evidenceId + '\'' +
                ", incidentId='" + incidentId + '\'' +
                ", ruleName='" + ruleName + '\'' +
                ", filePath='" + filePath + '\'' +
                '}';
    }

    public static final class Builder {
        private String evidenceId;
        private String incidentId;
        private String detectionId;
        private String ruleName;
        private String severity;
        private String confidence;
        private String filePath;
        private Instant detectedAt;
        private String correlationId;

        public Builder evidenceId(String evidenceId) { this.evidenceId = evidenceId; return this; }
        public Builder incidentId(String incidentId) { this.incidentId = incidentId; return this; }
        public Builder detectionId(String detectionId) { this.detectionId = detectionId; return this; }
        public Builder ruleName(String ruleName) { this.ruleName = ruleName; return this; }
        public Builder severity(String severity) { this.severity = severity; return this; }
        public Builder confidence(String confidence) { this.confidence = confidence; return this; }
        public Builder filePath(String filePath) { this.filePath = filePath; return this; }
        public Builder detectedAt(Instant detectedAt) { this.detectedAt = detectedAt; return this; }
        public Builder correlationId(String correlationId) { this.correlationId = correlationId; return this; }

        public EvidenceSummary build() {
            return new EvidenceSummary(this);
        }
    }
}
