package com.filex.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Persistence entity for security alerts.
 *
 * <p>Represents an alert generated from suspicious file activity. Alerts can be acknowledged by
 * users but are never deleted — they form part of the security audit trail.
 */
public final class AlertEntity {

  private final Long id;
  private final String alertId;
  private final Instant timestamp;
  private final String severity;
  private final String alertType;
  private final String title;
  private final String description;
  private final String fileEventId;
  private final boolean acknowledged;
  private final Instant acknowledgedAt;
  private final String acknowledgedBy;
  private final String metadata;
  private final Instant createdAt;

  private AlertEntity(Builder builder) {
    this.id = builder.id;
    this.alertId = Objects.requireNonNull(builder.alertId, "alertId must not be null");
    this.timestamp = Objects.requireNonNull(builder.timestamp, "timestamp must not be null");
    this.severity = Objects.requireNonNull(builder.severity, "severity must not be null");
    this.alertType = Objects.requireNonNull(builder.alertType, "alertType must not be null");
    this.title = Objects.requireNonNull(builder.title, "title must not be null");
    this.description = builder.description;
    this.fileEventId = builder.fileEventId;
    this.acknowledged = builder.acknowledged;
    this.acknowledgedAt = builder.acknowledgedAt;
    this.acknowledgedBy = builder.acknowledgedBy;
    this.metadata = builder.metadata;
    this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
  }

  // Getters
  public Long getId() {
    return id;
  }

  public String getAlertId() {
    return alertId;
  }

  public Instant getTimestamp() {
    return timestamp;
  }

  public String getSeverity() {
    return severity;
  }

  public String getAlertType() {
    return alertType;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public String getFileEventId() {
    return fileEventId;
  }

  public boolean isAcknowledged() {
    return acknowledged;
  }

  public Instant getAcknowledgedAt() {
    return acknowledgedAt;
  }

  public String getAcknowledgedBy() {
    return acknowledgedBy;
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

  public static final class Builder {
    private Long id;
    private String alertId;
    private Instant timestamp;
    private String severity;
    private String alertType;
    private String title;
    private String description;
    private String fileEventId;
    private boolean acknowledged;
    private Instant acknowledgedAt;
    private String acknowledgedBy;
    private String metadata;
    private Instant createdAt;

    public Builder id(Long id) {
      this.id = id;
      return this;
    }

    public Builder alertId(String alertId) {
      this.alertId = alertId;
      return this;
    }

    public Builder timestamp(Instant timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    public Builder severity(String severity) {
      this.severity = severity;
      return this;
    }

    public Builder alertType(String alertType) {
      this.alertType = alertType;
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

    public Builder fileEventId(String fileEventId) {
      this.fileEventId = fileEventId;
      return this;
    }

    public Builder acknowledged(boolean acknowledged) {
      this.acknowledged = acknowledged;
      return this;
    }

    public Builder acknowledgedAt(Instant acknowledgedAt) {
      this.acknowledgedAt = acknowledgedAt;
      return this;
    }

    public Builder acknowledgedBy(String acknowledgedBy) {
      this.acknowledgedBy = acknowledgedBy;
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

    public AlertEntity build() {
      return new AlertEntity(this);
    }
  }
}
