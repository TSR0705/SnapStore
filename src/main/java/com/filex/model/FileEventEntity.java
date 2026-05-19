package com.filex.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Persistence entity for file activity events.
 *
 * <p>Represents an immutable file system event captured by the monitoring engine. Events are never
 * modified after creation — they form an append-only audit log.
 *
 * <p>This is a persistence entity, not a UI model. UI layers should map this to view-specific
 * models as needed.
 */
public final class FileEventEntity {

  private final Long id;
  private final String eventId;
  private final Instant timestamp;
  private final String eventType;
  private final String filePath;
  private final Long fileSize;
  private final String fileHash;
  private final String processName;
  private final Integer processId;
  private final String userName;
  private final boolean suspicious;
  private final Double riskScore;
  private final String metadata;
  private final Instant createdAt;

  private FileEventEntity(Builder builder) {
    this.id = builder.id;
    this.eventId = Objects.requireNonNull(builder.eventId, "eventId must not be null");
    this.timestamp = Objects.requireNonNull(builder.timestamp, "timestamp must not be null");
    this.eventType = Objects.requireNonNull(builder.eventType, "eventType must not be null");
    this.filePath = Objects.requireNonNull(builder.filePath, "filePath must not be null");
    this.fileSize = builder.fileSize;
    this.fileHash = builder.fileHash;
    this.processName = builder.processName;
    this.processId = builder.processId;
    this.userName = builder.userName;
    this.suspicious = builder.suspicious;
    this.riskScore = builder.riskScore;
    this.metadata = builder.metadata;
    this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
  }

  // Getters
  public Long getId() {
    return id;
  }

  public String getEventId() {
    return eventId;
  }

  public Instant getTimestamp() {
    return timestamp;
  }

  public String getEventType() {
    return eventType;
  }

  public String getFilePath() {
    return filePath;
  }

  public Long getFileSize() {
    return fileSize;
  }

  public String getFileHash() {
    return fileHash;
  }

  public String getProcessName() {
    return processName;
  }

  public Integer getProcessId() {
    return processId;
  }

  public String getUserName() {
    return userName;
  }

  public boolean isSuspicious() {
    return suspicious;
  }

  public Double getRiskScore() {
    return riskScore;
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
    private String eventId;
    private Instant timestamp;
    private String eventType;
    private String filePath;
    private Long fileSize;
    private String fileHash;
    private String processName;
    private Integer processId;
    private String userName;
    private boolean suspicious;
    private Double riskScore;
    private String metadata;
    private Instant createdAt;

    public Builder id(Long id) {
      this.id = id;
      return this;
    }

    public Builder eventId(String eventId) {
      this.eventId = eventId;
      return this;
    }

    public Builder timestamp(Instant timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    public Builder eventType(String eventType) {
      this.eventType = eventType;
      return this;
    }

    public Builder filePath(String filePath) {
      this.filePath = filePath;
      return this;
    }

    public Builder fileSize(Long fileSize) {
      this.fileSize = fileSize;
      return this;
    }

    public Builder fileHash(String fileHash) {
      this.fileHash = fileHash;
      return this;
    }

    public Builder processName(String processName) {
      this.processName = processName;
      return this;
    }

    public Builder processId(Integer processId) {
      this.processId = processId;
      return this;
    }

    public Builder userName(String userName) {
      this.userName = userName;
      return this;
    }

    public Builder suspicious(boolean suspicious) {
      this.suspicious = suspicious;
      return this;
    }

    public Builder riskScore(Double riskScore) {
      this.riskScore = riskScore;
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

    public FileEventEntity build() {
      return new FileEventEntity(this);
    }
  }
}
