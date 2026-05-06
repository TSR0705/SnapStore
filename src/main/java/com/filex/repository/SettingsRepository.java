package com.filex.repository;

import com.filex.model.AppSettingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

/**
 * Repository for application settings persistence.
 *
 * <p>Provides type-safe get/set operations for common setting types.
 * All settings are stored as strings with type metadata for proper
 * deserialization.
 */
public final class SettingsRepository {

    private static final Logger log = LoggerFactory.getLogger(SettingsRepository.class);

    private final Connection connection;

    public SettingsRepository(Connection connection) {
        this.connection = connection;
    }

    /**
     * Saves or updates a setting.
     */
    public void save(String key, String value, String valueType, String description) throws SQLException {
        String sql = """
                INSERT INTO app_settings (key, value, value_type, description, updated_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(key) DO UPDATE SET
                    value = excluded.value,
                    value_type = excluded.value_type,
                    description = excluded.description,
                    updated_at = excluded.updated_at
                """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, key);
            pstmt.setString(2, value);
            pstmt.setString(3, valueType);
            pstmt.setString(4, description);
            pstmt.setLong(5, Instant.now().toEpochMilli());

            pstmt.executeUpdate();
            log.debug("Saved setting: key={}", key);
        }
    }

    /**
     * Retrieves a setting by key.
     */
    public Optional<AppSettingEntity> findByKey(String key) throws SQLException {
        String sql = "SELECT * FROM app_settings WHERE key = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, key);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Retrieves a string setting value.
     */
    public Optional<String> getString(String key) throws SQLException {
        return findByKey(key).map(AppSettingEntity::getValue);
    }

    /**
     * Retrieves an integer setting value.
     */
    public Optional<Integer> getInt(String key) throws SQLException {
        return getString(key).map(Integer::parseInt);
    }

    /**
     * Retrieves a boolean setting value.
     */
    public Optional<Boolean> getBoolean(String key) throws SQLException {
        return getString(key).map(Boolean::parseBoolean);
    }

    /**
     * Saves a string setting.
     */
    public void saveString(String key, String value, String description) throws SQLException {
        save(key, value, "STRING", description);
    }

    /**
     * Saves an integer setting.
     */
    public void saveInt(String key, int value, String description) throws SQLException {
        save(key, String.valueOf(value), "INTEGER", description);
    }

    /**
     * Saves a boolean setting.
     */
    public void saveBoolean(String key, boolean value, String description) throws SQLException {
        save(key, String.valueOf(value), "BOOLEAN", description);
    }

    /**
     * Deletes a setting.
     */
    public void delete(String key) throws SQLException {
        String sql = "DELETE FROM app_settings WHERE key = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, key);
            int deleted = pstmt.executeUpdate();
            if (deleted > 0) {
                log.debug("Deleted setting: key={}", key);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private AppSettingEntity mapRow(ResultSet rs) throws SQLException {
        long updatedAtMillis = rs.getLong("updated_at");
        Instant updatedAt = Instant.ofEpochMilli(updatedAtMillis);
        
        return new AppSettingEntity(
                rs.getString("key"),
                rs.getString("value"),
                rs.getString("value_type"),
                rs.getString("description"),
                updatedAt
        );
    }
}
