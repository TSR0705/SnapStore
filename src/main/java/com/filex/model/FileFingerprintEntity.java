package com.filex.model;

import java.time.Instant;

/**
 * Entity representing a file fingerprint record.
 *
 * <p>Stores file hash and metadata for deduplication and change detection.
 * Immutable value object with builder pattern.
 */
public final class FileFingerprintEntity {

    private final Long id;
    private final String filePath;
    private final String fileHash;
    private final long fileSize;
    private final Instant lastModified;
    private final Instant firstSeen;
    private final Instant lastSeen;
    private final int scanCount;
    private final String metadata;

    private FileFingerprintEntity(Builder builder) {
        this.id = builder.id;
        this.filePath = builder.filePath;
        this.fileHash = builder.fileHash;
        this.fileSize = builder.fileSize;
        this.lastModified = builder.lastModified;
        this.firstSeen = builder.firstSeen;
        this.lastSeen = builder.lastSeen;
        this.scanCount = builder.scanCount;
        this.metadata = builder.metadata;
    }

    public Long getId() {
        return id;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getFileHash() {
        return fileHash;
    }

    public long getFileSize() {
        return fileSize;
    }

    public Instant getLastModified() {
        return lastModified;
    }

    public Instant getFirstSeen() {
        return firstSeen;
    }

    public Instant getLastSeen() {
        return lastSeen;
    }

    public int getScanCount() {
        return scanCount;
    }

    public String getMetadata() {
        return metadata;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Long id;
        private String filePath;
        private String fileHash;
        private long fileSize;
        private Instant lastModified;
        private Instant firstSeen;
        private Instant lastSeen;
        private int scanCount;
        private String metadata;

        private Builder() {
        }

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder filePath(String filePath) {
            this.filePath = filePath;
            return this;
        }

        public Builder fileHash(String fileHash) {
            this.fileHash = fileHash;
            return this;
        }

        public Builder fileSize(long fileSize) {
            this.fileSize = fileSize;
            return this;
        }

        public Builder lastModified(Instant lastModified) {
            this.lastModified = lastModified;
            return this;
        }

        public Builder firstSeen(Instant firstSeen) {
            this.firstSeen = firstSeen;
            return this;
        }

        public Builder lastSeen(Instant lastSeen) {
            this.lastSeen = lastSeen;
            return this;
        }

        public Builder scanCount(int scanCount) {
            this.scanCount = scanCount;
            return this;
        }

        public Builder metadata(String metadata) {
            this.metadata = metadata;
            return this;
        }

        public FileFingerprintEntity build() {
            return new FileFingerprintEntity(this);
        }
    }
}
