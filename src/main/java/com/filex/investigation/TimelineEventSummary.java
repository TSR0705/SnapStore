package com.filex.investigation;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable summary of a timeline event for investigation purposes.
 *
 * <p>Provides a lightweight view of timeline data optimized for replay navigation and chronological
 * analysis.
 *
 * <p>Thread-safety: Immutable after construction.
 */
public final class TimelineEventSummary {

  private final String timelineId;
  private final Instant timestamp;
  private final long sequenceNumber;
  private final String eventType;
  private final String incidentId;
  private final String evidenceId;
  private final String severity;
  private final String description;
  private final String correlationId;

  private TimelineEventSummary(Builder builder) {
    this.timelineId = Objects.requireNonNull(builder.timelineId, "timelineId must not be null");
    this.timestamp = Objects.requireNonNull(builder.timestamp, "timestamp must not be null");
    this.sequenceNumber = builder.sequenceNumber;
    this.eventType = Objects.requireNonNull(builder.eventType, "eventType must not be null");
    this.incidentId = builder.incidentId;
    this.evidenceId = builder.evidenceId;
    this.severity = builder.severity;
    this.description = builder.description;
    this.correlationId = builder.correlationId;
  }

  // Getters
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

  public String getSeverity() {
    return severity;
  }

  public String getDescription() {
    return description;
  }

  public String getCorrelationId() {
    return correlationId;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public String toString() {
    return "TimelineEventSummary{"
        + "timelineId='"
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
    private String timelineId;
    private Instant timestamp;
    private long sequenceNumber;
    private String eventType;
    private String incidentId;
    private String evidenceId;
    private String severity;
    private String description;
    private String correlationId;

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

    public TimelineEventSummary build() {
      return new TimelineEventSummary(this);
    }
  }
}
