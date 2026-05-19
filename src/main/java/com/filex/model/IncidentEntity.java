package com.filex.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Persistence entity for security incidents.
 *
 * <p>Represents a correlated security incident that aggregates one or more related detections.
 * Incidents form the core of the forensic timeline and are immutable once persisted (updates create
 * new versions).
 *
 * <p>Thread-safety: Immutable after construction.
 */
public final class IncidentEntity {

  private final Long id;
  private final String incidentId;
  private final String severity;
  private final String confidence;
  private final String status;
  private final String title;
  private final String description;
  private final Instant createdAt;
  private final Instant updatedAt;
  private final Instant lastSeenAt;
  private final String correlationId;
  private final int escalationLevel;
  private final int detectionCount;
  private final String metadata;

  private IncidentEntity(Builder builder) {
    this.id = builder.id;
    this.incidentId = Objects.requireNonNull(builder.incidentId, "incidentId must not be null");
    this.severity = Objects.requireNonNull(builder.severity, "severity must not be null");
    this.confidence = Objects.requireNonNull(builder.confidence, "confidence must not be null");
    this.status = Objects.requireNonNull(builder.status, "status must not be null");
    this.title = Objects.requireNonNull(builder.title, "title must not be null");
    this.description = builder.description;
    this.createdAt = Objects.requireNonNull(builder.createdAt, "createdAt must not be null");
    this.updatedAt = Objects.requireNonNull(builder.updatedAt, "updatedAt must not be null");
    this.lastSeenAt = Objects.requireNonNull(builder.lastSeenAt, "lastSeenAt must not be null");
    this.correlationId = builder.correlationId;
    this.escalationLevel = builder.escalationLevel;
    this.detectionCount = builder.detectionCount;
    this.metadata = builder.metadata;
  }

  // Getters
  public Long getId() {
    return id;
  }

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

  public String getCorrelationId() {
    return correlationId;
  }

  public int getEscalationLevel() {
    return escalationLevel;
  }

  public int getDetectionCount() {
    return detectionCount;
  }

  public String getMetadata() {
    return metadata;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public String toString() {
    return "IncidentEntity{"
        + "id="
        + id
        + ", incidentId='"
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
        + '}';
  }

  public static final class Builder {
    private Long id;
    private String incidentId;
    private String severity;
    private String confidence;
    private String status;
    private String title;
    private String description;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastSeenAt;
    private String correlationId;
    private int escalationLevel;
    private int detectionCount;
    private String metadata;

    public Builder id(Long id) {
      this.id = id;
      return this;
    }

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

    public Builder metadata(String metadata) {
      this.metadata = metadata;
      return this;
    }

    public IncidentEntity build() {
      return new IncidentEntity(this);
    }
  }
}
