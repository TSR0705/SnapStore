package com.filex.investigation;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable summary of an incident for investigation purposes.
 *
 * <p>Provides a lightweight view of incident data optimized for investigation queries and forensic
 * analysis. Does not include full metadata to reduce memory footprint during large-scale queries.
 *
 * <p>Thread-safety: Immutable after construction.
 */
public final class IncidentSummary {

  private final String incidentId;
  private final String severity;
  private final String confidence;
  private final String status;
  private final String title;
  private final Instant createdAt;
  private final Instant updatedAt;
  private final Instant lastSeenAt;
  private final String correlationId;
  private final int escalationLevel;
  private final int detectionCount;
  private final long evidenceCount;

  private IncidentSummary(Builder builder) {
    this.incidentId = Objects.requireNonNull(builder.incidentId, "incidentId must not be null");
    this.severity = Objects.requireNonNull(builder.severity, "severity must not be null");
    this.confidence = Objects.requireNonNull(builder.confidence, "confidence must not be null");
    this.status = Objects.requireNonNull(builder.status, "status must not be null");
    this.title = Objects.requireNonNull(builder.title, "title must not be null");
    this.createdAt = Objects.requireNonNull(builder.createdAt, "createdAt must not be null");
    this.updatedAt = Objects.requireNonNull(builder.updatedAt, "updatedAt must not be null");
    this.lastSeenAt = Objects.requireNonNull(builder.lastSeenAt, "lastSeenAt must not be null");
    this.correlationId = builder.correlationId;
    this.escalationLevel = builder.escalationLevel;
    this.detectionCount = builder.detectionCount;
    this.evidenceCount = builder.evidenceCount;
  }

  // Getters
  public String getIncidentId() {
    return incidentId;
  }

  public String getSeverity() {
    return severity;
  }

  public String getConfidence() {
    return confidence;
  }

  public String getStatus() {
    return status;
  }

  public String getTitle() {
    return title;
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

  public String getCorrelationId() {
    return correlationId;
  }

  public int getEscalationLevel() {
    return escalationLevel;
  }

  public int getDetectionCount() {
    return detectionCount;
  }

  public long getEvidenceCount() {
    return evidenceCount;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public String toString() {
    return "IncidentSummary{"
        + "incidentId='"
        + incidentId
        + '\''
        + ", severity='"
        + severity
        + '\''
        + ", status='"
        + status
        + '\''
        + ", title='"
        + title
        + '\''
        + ", evidenceCount="
        + evidenceCount
        + '}';
  }

  public static final class Builder {
    private String incidentId;
    private String severity;
    private String confidence;
    private String status;
    private String title;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastSeenAt;
    private String correlationId;
    private int escalationLevel;
    private int detectionCount;
    private long evidenceCount;

    public Builder incidentId(String incidentId) {
      this.incidentId = incidentId;
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

    public Builder status(String status) {
      this.status = status;
      return this;
    }

    public Builder title(String title) {
      this.title = title;
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

    public Builder correlationId(String correlationId) {
      this.correlationId = correlationId;
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

    public Builder evidenceCount(long evidenceCount) {
      this.evidenceCount = evidenceCount;
      return this;
    }

    public IncidentSummary build() {
      return new IncidentSummary(this);
    }
  }
}
