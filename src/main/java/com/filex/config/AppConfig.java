package com.filex.config;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Immutable application configuration record.
 *
 * <p>Holds all resolved paths and settings for the current runtime.
 * Constructed once by {@link ConfigManager} and passed through the
 * dependency graph — never accessed statically.
 */
public record AppConfig(
        Path appHome,
        Path logsDir,
        Path dataDir,
        Path configDir,
        Path databaseFile,
        String appName,
        String appVersion,
        boolean debugMode
) {

    /** Canonical database filename. */
    public static final String DB_FILENAME = "filex.db";

    /** Application name constant. */
    public static final String APP_NAME = "FileX";

    /** Application version constant. */
    public static final String APP_VERSION = "1.0.0-SNAPSHOT";

    /**
     * Validates that all required paths are non-null.
     * Java record compact constructor.
     */
    public AppConfig {
        if (appHome == null)    throw new IllegalArgumentException("appHome must not be null");
        if (logsDir == null)    throw new IllegalArgumentException("logsDir must not be null");
        if (dataDir == null)    throw new IllegalArgumentException("dataDir must not be null");
        if (configDir == null)  throw new IllegalArgumentException("configDir must not be null");
        if (databaseFile == null) throw new IllegalArgumentException("databaseFile must not be null");
        if (appName == null || appName.isBlank()) throw new IllegalArgumentException("appName must not be blank");
        if (appVersion == null || appVersion.isBlank()) throw new IllegalArgumentException("appVersion must not be blank");
    }

    /**
     * Returns a human-readable summary of the resolved configuration.
     */
    public String summary() {
        return String.format(
                "[AppConfig] name=%s version=%s home=%s debug=%b",
                appName, appVersion, appHome, debugMode
        );
    }
}
