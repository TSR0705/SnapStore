package com.filex.model;

import java.time.Instant;

/**
 * Entity representing a sync queue record.
 *
 * <p>Supports offline-first architecture by queuing operations
 * for eventual backend synchronization. Immutable value object
 * with builder pattern.
 */
public final class SyncQueueEntity {

    private final Long id;
    private final String entityType;
    private final String entityId;
    private final String operation;
    private final String payload;
    private final String status;
    private final int retryCount;
    private final Instant lastAttempt;
    private final String errorMessage;
    private final Instant createdAt;
    private final Instant syncedAt;

    private SyncQueueEntity(Builder builder) {
        this.id = builder.id;
        this.entityType = builder.entityType;
        this.entityId = builder.entityId;
        this.operation = builder.operation;
        this.payload = builder.payload;
        this.status = builder.status;
        this.retryCount = builder.retryCount;
        this.lastAttempt = builder.lastAttempt;
        this.errorMessage = builder.errorMessage;
        this.createdAt = builder.createdAt;
        this.syncedAt = builder.syncedAt;
    }

    public Long getId() {
        return id;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getOperation() {
        return operation;
    }

    public String getPayload() {
        return payload;
    }

    public String getStatus() {
        return status;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public Instant getLastAttempt() {
        return lastAttempt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Long id;
        private String entityType;
        private String entityId;
        private String operation;
        private String payload;
        private String status;
        private int retryCount;
        private Instant lastAttempt;
        private String errorMessage;
        private Instant createdAt;
        private Instant syncedAt;

        private Builder() {
        }

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder entityType(String entityType) {
            this.entityType = entityType;
            return this;
        }

        public Builder entityId(String entityId) {
            this.entityId = entityId;
            return this;
        }

        public Builder operation(String operation) {
            this.operation = operation;
            return this;
        }

        public Builder payload(String payload) {
            this.payload = payload;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder retryCount(int retryCount) {
            this.retryCount = retryCount;
            return this;
        }

        public Builder lastAttempt(Instant lastAttempt) {
            this.lastAttempt = lastAttempt;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder syncedAt(Instant syncedAt) {
            this.syncedAt = syncedAt;
            return this;
        }

        public SyncQueueEntity build() {
            return new SyncQueueEntity(this);
        }
    }
}
