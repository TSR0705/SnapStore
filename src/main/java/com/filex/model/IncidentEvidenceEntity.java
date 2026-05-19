package com.filex.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Persistence entity for incident evidence records.
 *
 * <p>Links detection events to incidents, forming an immutable evidence chain. Each evidence record
 * represents a single detection that contributed to an incident.
 *
 * <p>Evidence records are append-only and never modified, preserving forensic integrity.
 *
 * <p>Thread-safety: Immutable after construction.
 */
public final class IncidentEvidenceEntity {

  private final Long id;
  private final String evidenceId;
  private final String incidentId;
  private final String detectionId;
  private final String ruleName;
  private final String severity;
  private final String confidence;
  private final String filePath;
  private final Instant detectedAt;
  private final String correlationId;
  private final String metadata;
  private final Instant createdAt;

  private IncidentEvidenceEntity(Builder builder) {
    this.id = builder.id;
    this.evidenceId = Objects.requireNonNull(builder.evidenceId, "evidenceId must not be null");
    this.incidentId = Objects.requireNonNull(builder.incidentId, "incidentId must not be null");
    this.detectionId = Objects.requireNonNull(builder.detectionId, "detectionId must not be null");
    this.ruleName = Objects.requireNonNull(builder.ruleName, "ruleName must not be null");
    this.severity = Objects.requireNonNull(builder.severity, "severity must not be null");
    this.confidence = Objects.requireNonNull(builder.confidence, "confidence must not be null");
    this.filePath = builder.filePath;
    this.detectedAt = Objects.requireNonNull(builder.detectedAt, "detectedAt must not be null");
    this.correlationId = builder.correlationId;
    this.metadata = builder.metadata;
    this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
  }

  // Getters
  public Long getId() {
    return id;
  }

  public String getEvidenceId() {
    return evidenceId;
  }

  public String getIncidentId() {
    return incidentId;
  }

  public String getDetectionId() {
    return detectionId;
  }

  public String getRuleName() {
    return ruleName;
  }

  public String getSeverity() {
    return severity;
  }

  public String getConfidence() {
    return confidence;
  }

  public String getFilePath() {
    return filePath;
  }

  public Instant getDetectedAt() {
    return detectedAt;
  }

  public String getCorrelationId() {
    return correlationId;
  }

  public String getMetadata() {
    return metadata;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public String toString() {
    return "IncidentEvidenceEntity{"
        + "id="
        + id
        + ", evidenceId='"
        + evidenceId
        + '\''
        + ", incidentId='"
        + incidentId
        + '\''
        + ", detectionId='"
        + detectionId
        + '\''
        + ", ruleName='"
        + ruleName
        + '\''
        + '}';
  }

  public static final class Builder {
    private Long id;
    private String evidenceId;
    private String incidentId;
    private String detectionId;
    private String ruleName;
    private String severity;
    private String confidence;
    private String filePath;
    private Instant detectedAt;
    private String correlationId;
    private String metadata;
    private Instant createdAt;

    public Builder id(Long id) {
      this.id = id;
      return this;
    }

    public Builder evidenceId(String evidenceId) {
      this.evidenceId = evidenceId;
      return this;
    }

    public Builder incidentId(String incidentId) {
      this.incidentId = incidentId;
      return this;
    }

    public Builder detectionId(String detectionId) {
      this.detectionId = detectionId;
      return this;
    }

    public Builder ruleName(String ruleName) {
      this.ruleName = ruleName;
      return this;
    }

    public Builder severity(String severity) {
      this.severity = severity;
      return this;
    }

    public Builder confidence(String confidence) {
      this.confidence = confidence;
      return this;
    }

    public Builder filePath(String filePath) {
      this.filePath = filePath;
      return this;
    }

    public Builder detectedAt(Instant detectedAt) {
      this.detectedAt = detectedAt;
      return this;
    }

    public Builder correlationId(String correlationId) {
      this.correlationId = correlationId;
      return this;
    }

    public Builder metadata(String metadata) {
      this.metadata = metadata;
      return this;
    }

    public Builder createdAt(Instant createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public IncidentEvidenceEntity build() {
      return new IncidentEvidenceEntity(this);
    }
  }
}
