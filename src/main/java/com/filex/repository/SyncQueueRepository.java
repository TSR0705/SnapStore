package com.filex.repository;

import com.filex.model.SyncQueueEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link SyncQueueEntity} persistence operations.
 *
 * <p>Manages the offline-first sync queue for backend synchronization.
 * All SQL is encapsulated here with PreparedStatement for safety.
 */
public final class SyncQueueRepository {

    private static final Logger log = LoggerFactory.getLogger(SyncQueueRepository.class);

    private final Connection connection;

    public SyncQueueRepository(Connection connection) {
        this.connection = connection;
    }

    /**
     * Inserts a new sync queue entry.
     *
     * @return the generated ID
     */
    public long insert(SyncQueueEntity entry) throws SQLException {
        String sql = """
                INSERT INTO sync_queue (
                    entity_type, entity_id, operation, payload, status,
                    retry_count, last_attempt, error_message, synced_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, entry.getEntityType());
            pstmt.setString(2, entry.getEntityId());
            pstmt.setString(3, entry.getOperation());
            pstmt.setString(4, entry.getPayload());
            pstmt.setString(5, entry.getStatus());
            pstmt.setInt(6, entry.getRetryCount());
            pstmt.setObject(7, entry.getLastAttempt() != null ? entry.getLastAttempt().toEpochMilli() : null);
            pstmt.setString(8, entry.getErrorMessage());
            pstmt.setObject(9, entry.getSyncedAt() != null ? entry.getSyncedAt().toEpochMilli() : null);

            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    log.debug("Inserted sync queue entry: id={}, entityType={}", id, entry.getEntityType());
                    return id;
                }
            }
        }

        throw new SQLException("Failed to retrieve generated ID for sync queue entry");
    }

    /**
     * Finds a sync queue entry by ID.
     */
    public Optional<SyncQueueEntity> findById(long id) throws SQLException {
        String sql = "SELECT * FROM sync_queue WHERE id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, id);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Finds all pending sync queue entries (status = 'pending').
     */
    public List<SyncQueueEntity> findPending(int limit) throws SQLException {
        List<SyncQueueEntity> entries = new ArrayList<>();

        String sql = "SELECT * FROM sync_queue WHERE status = 'pending' ORDER BY created_at ASC LIMIT ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, limit);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    entries.add(mapRow(rs));
                }
            }
        }

        return entries;
    }

    /**
     * Finds all failed sync queue entries (status = 'failed').
     */
    public List<SyncQueueEntity> findFailed(int limit) throws SQLException {
        List<SyncQueueEntity> entries = new ArrayList<>();

        String sql = "SELECT * FROM sync_queue WHERE status = 'failed' ORDER BY created_at ASC LIMIT ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, limit);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    entries.add(mapRow(rs));
                }
            }
        }

        return entries;
    }

    /**
     * Updates the status of a sync queue entry.
     */
    public void updateStatus(long id, String status, String errorMessage) throws SQLException {
        String sql = """
                UPDATE sync_queue
                SET status = ?,
                    last_attempt = ?,
                    error_message = ?,
                    retry_count = retry_count + 1
                WHERE id = ?
                """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, status);
            pstmt.setLong(2, Instant.now().toEpochMilli());
            pstmt.setString(3, errorMessage);
            pstmt.setLong(4, id);

            int updated = pstmt.executeUpdate();
            if (updated > 0) {
                log.debug("Updated sync queue entry: id={}, status={}", id, status);
            }
        }
    }

    /**
     * Marks a sync queue entry as synced.
     */
    public void markSynced(long id) throws SQLException {
        String sql = """
                UPDATE sync_queue
                SET status = 'synced',
                    synced_at = ?
                WHERE id = ?
                """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, Instant.now().toEpochMilli());
            pstmt.setLong(2, id);

            int updated = pstmt.executeUpdate();
            if (updated > 0) {
                log.debug("Marked sync queue entry as synced: id={}", id);
            }
        }
    }

    /**
     * Deletes synced entries older than the specified timestamp.
     */
    public int deleteSyncedBefore(Instant before) throws SQLException {
        String sql = "DELETE FROM sync_queue WHERE status = 'synced' AND synced_at < ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, before.toEpochMilli());
            int deleted = pstmt.executeUpdate();
            if (deleted > 0) {
                log.debug("Deleted {} synced entries older than {}", deleted, before);
            }
            return deleted;
        }
    }

    /**
     * Counts total sync queue entries.
     */
    public long count() throws SQLException {
        String sql = "SELECT COUNT(*) FROM sync_queue";

        try (PreparedStatement pstmt = connection.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }

        return 0;
    }

    /**
     * Counts pending sync queue entries.
     */
    public long countPending() throws SQLException {
        String sql = "SELECT COUNT(*) FROM sync_queue WHERE status = 'pending'";

        try (PreparedStatement pstmt = connection.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }

        return 0;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private SyncQueueEntity mapRow(ResultSet rs) throws SQLException {
        // Handle nullable Instant for last_attempt
        Instant lastAttempt = null;
        long lastAttemptValue = rs.getLong("last_attempt");
        if (!rs.wasNull()) {
            lastAttempt = Instant.ofEpochMilli(lastAttemptValue);
        }

        // Handle nullable Instant for synced_at
        Instant syncedAt = null;
        long syncedAtValue = rs.getLong("synced_at");
        if (!rs.wasNull()) {
            syncedAt = Instant.ofEpochMilli(syncedAtValue);
        }

        long createdAtMillis = rs.getLong("created_at");
        Instant createdAt = Instant.ofEpochMilli(createdAtMillis);

        return SyncQueueEntity.builder()
                .id(rs.getLong("id"))
                .entityType(rs.getString("entity_type"))
                .entityId(rs.getString("entity_id"))
                .operation(rs.getString("operation"))
                .payload(rs.getString("payload"))
                .status(rs.getString("status"))
                .retryCount(rs.getInt("retry_count"))
                .lastAttempt(lastAttempt)
                .errorMessage(rs.getString("error_message"))
                .createdAt(createdAt)
                .syncedAt(syncedAt)
                .build();
    }
}
