package com.filex.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Persistence entity for forensic timeline records.
 *
 * <p>Timeline records provide chronological reconstruction of security events, linking incidents,
 * evidence, and detections in a deterministic order.
 *
 * <p>Timeline records are immutable and ordered by:
 *
 * <ol>
 *   <li>Timestamp (primary ordering)
 *   <li>Sequence number (deterministic tie-breaking)
 * </ol>
 *
 * <p>Thread-safety: Immutable after construction.
 */
public final class ForensicTimelineEntity {

  private final Long id;
  private final String timelineId;
  private final Instant timestamp;
  private final long sequenceNumber;
  private final String eventType;
  private final String incidentId;
  private final String evidenceId;
  private final String detectionId;
  private final String severity;
  private final String description;
  private final String correlationId;
  private final String metadata;
  private final Instant createdAt;

  private ForensicTimelineEntity(Builder builder) {
    this.id = builder.id;
    this.timelineId = Objects.requireNonNull(builder.timelineId, "timelineId must not be null");
    this.timestamp = Objects.requireNonNull(builder.timestamp, "timestamp must not be null");
    this.sequenceNumber = builder.sequenceNumber;
    this.eventType = Objects.requireNonNull(builder.eventType, "eventType must not be null");
    this.incidentId = builder.incidentId;
    this.evidenceId = builder.evidenceId;
    this.detectionId = builder.detectionId;
    this.severity = builder.severity;
    this.description = builder.description;
    this.correlationId = builder.correlationId;
    this.metadata = builder.metadata;
    this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
  }

  // Getters
  public Long getId() {
    return id;
  }

  public String getTimelineId() {
    return timelineId;
  }

  public Instant getTimestamp() {
    return timestamp;
  }

  public long getSequenceNumber() {
    return sequenceNumber;
  }

  public String getEventType() {
    return eventType;
  }

  public String getIncidentId() {
    return incidentId;
  }

  public String getEvidenceId() {
    return evidenceId;
  }

  public String getDetectionId() {
    return detectionId;
  }

  public String getSeverity() {
    return severity;
  }

  public String getDescription() {
    return description;
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
    return "ForensicTimelineEntity{"
        + "id="
        + id
        + ", timelineId='"
        + timelineId
        + '\''
        + ", timestamp="
        + timestamp
        + ", sequenceNumber="
        + sequenceNumber
        + ", eventType='"
        + eventType
        + '\''
        + ", incidentId='"
        + incidentId
        + '\''
        + '}';
  }

  public static final class Builder {
    private Long id;
    private String timelineId;
    private Instant timestamp;
    private long sequenceNumber;
    private String eventType;
    private String incidentId;
    private String evidenceId;
    private String detectionId;
    private String severity;
    private String description;
    private String correlationId;
    private String metadata;
    private Instant createdAt;

    public Builder id(Long id) {
      this.id = id;
      return this;
    }

    public Builder timelineId(String timelineId) {
      this.timelineId = timelineId;
      return this;
    }

    public Builder timestamp(Instant timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    public Builder sequenceNumber(long sequenceNumber) {
      this.sequenceNumber = sequenceNumber;
      return this;
    }

    public Builder eventType(String eventType) {
      this.eventType = eventType;
      return this;
    }

    public Builder incidentId(String incidentId) {
      this.incidentId = incidentId;
      return this;
    }

    public Builder evidenceId(String evidenceId) {
      this.evidenceId = evidenceId;
      return this;
    }

    public Builder detectionId(String detectionId) {
      this.detectionId = detectionId;
      return this;
    }

    public Builder severity(String severity) {
      this.severity = severity;
      return this;
    }

    public Builder description(String description) {
      this.description = description;
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

    public ForensicTimelineEntity build() {
      return new ForensicTimelineEntity(this);
    }
  }
}
