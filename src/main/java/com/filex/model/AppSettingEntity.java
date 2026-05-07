package com.filex.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Persistence entity for application settings.
 *
 * <p>Settings are stored as key-value pairs with type information
 * to support proper deserialization.
 */
public final class AppSettingEntity {

    private final String key;
    private final String value;
    private final String valueType;
    private final String description;
    private final Instant updatedAt;

    public AppSettingEntity(String key, String value, String valueType, String description, Instant updatedAt) {
        this.key = Objects.requireNonNull(key, "key must not be null");
        this.value = Objects.requireNonNull(value, "value must not be null");
        this.valueType = Objects.requireNonNull(valueType, "valueType must not be null");
        this.description = description;
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public String getKey() { return key; }
    public String getValue() { return value; }
    public String getValueType() { return valueType; }
    public String getDescription() { return description; }
    public Instant getUpdatedAt() { return updatedAt; }
}
