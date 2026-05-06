package com.filex.database;

import com.filex.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages the SQLite database connection lifecycle.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Open and validate the SQLite connection on startup</li>
 *   <li>Run schema bootstrap (DDL) on first launch</li>
 *   <li>Provide a single shared {@link Connection} for the application</li>
 *   <li>Close the connection cleanly on shutdown</li>
 * </ul>
 *
 * <p>Design notes:
 * <ul>
 *   <li>A single shared connection is appropriate for a desktop agent
 *       with low concurrency. Connection pooling is deferred to later phases.</li>
 *   <li>WAL mode is enabled for better read/write concurrency.</li>
 *   <li>Foreign key enforcement is enabled explicitly.</li>
 * </ul>
 *
 * <p>Repositories obtain connections via {@link #getConnection()} and
 * must NOT close the connection — only the manager closes it.
 */
public final class DatabaseManager {

    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);

    private static final String JDBC_PREFIX = "jdbc:sqlite:";

    private final Path databaseFile;
    private Connection connection;

    /**
     * @param config resolved application configuration; provides the DB path
     */
    public DatabaseManager(AppConfig config) {
        this.databaseFile = config.databaseFile();
    }

    /**
     * Opens the SQLite connection and bootstraps the schema.
     *
     * @throws DatabaseException if the connection cannot be established
     */
    public void initialize() {
        log.info("Initializing SQLite database at: {}", databaseFile);

        ensureParentDirectoryExists();

        try {
            String jdbcUrl = JDBC_PREFIX + databaseFile.toAbsolutePath();
            connection = DriverManager.getConnection(jdbcUrl);
            log.info("SQLite connection established.");

            applyPragmas();
            bootstrapSchema();

            log.info("Database initialization complete.");
        } catch (SQLException e) {
            throw new DatabaseException("Failed to initialize SQLite database: " + databaseFile, e);
        }
    }

    /**
     * Returns the active database connection.
     *
     * @throws IllegalStateException if called before {@link #initialize()}
     */
    public Connection getConnection() {
        if (connection == null) {
            throw new IllegalStateException("DatabaseManager has not been initialized. Call initialize() first.");
        }
        return connection;
    }

    /**
     * Closes the database connection. Safe to call multiple times.
     */
    public void shutdown() {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                    log.info("SQLite connection closed.");
                }
            } catch (SQLException e) {
                log.error("Error closing SQLite connection: {}", e.getMessage(), e);
            } finally {
                connection = null;
            }
        }
    }

    /**
     * Returns {@code true} if the connection is open and valid.
     */
    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed() && connection.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void ensureParentDirectoryExists() {
        Path parent = databaseFile.getParent();
        if (parent != null && !Files.exists(parent)) {
            try {
                Files.createDirectories(parent);
                log.debug("Created database parent directory: {}", parent);
            } catch (Exception e) {
                throw new DatabaseException("Cannot create database directory: " + parent, e);
            }
        }
    }

    private void applyPragmas() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Write-Ahead Logging for better concurrency
            stmt.execute("PRAGMA journal_mode=WAL;");
            // Enforce foreign key constraints
            stmt.execute("PRAGMA foreign_keys=ON;");
            // Synchronous mode: NORMAL is a good balance for desktop apps
            stmt.execute("PRAGMA synchronous=NORMAL;");
            log.debug("SQLite pragmas applied: WAL, foreign_keys=ON, synchronous=NORMAL");
        }
    }

    /**
     * Creates the baseline schema tables if they do not already exist.
     *
     * <p>Phase 1A only creates the metadata/audit table. Domain tables
     * (events, alerts, snapshots) are added in later phases.
     */
    private void bootstrapSchema() throws SQLException {
        log.debug("Bootstrapping database schema...");

        try (Statement stmt = connection.createStatement()) {
            // Schema version tracking — used for future migrations
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS schema_version (
                        id          INTEGER PRIMARY KEY AUTOINCREMENT,
                        version     TEXT    NOT NULL,
                        applied_at  TEXT    NOT NULL DEFAULT (datetime('now')),
                        description TEXT
                    );
                    """);

            // Application startup audit log
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS app_startup_log (
                        id           INTEGER PRIMARY KEY AUTOINCREMENT,
                        started_at   TEXT    NOT NULL DEFAULT (datetime('now')),
                        app_version  TEXT    NOT NULL,
                        hostname     TEXT,
                        os_name      TEXT,
                        java_version TEXT
                    );
                    """);

            recordStartup(stmt);
        }

        log.info("Schema bootstrap complete.");
    }

    private void recordStartup(Statement stmt) throws SQLException {
        String appVersion = escapeString(System.getProperty("filex.version", "1.0.0-SNAPSHOT"));
        String hostname   = escapeString(getHostname());
        String osName     = escapeString(System.getProperty("os.name", "unknown"));
        String javaVer    = escapeString(System.getProperty("java.version", "unknown"));

        stmt.execute(String.format(
                "INSERT INTO app_startup_log (app_version, hostname, os_name, java_version) " +
                "VALUES ('%s', '%s', '%s', '%s');",
                appVersion, hostname, osName, javaVer
        ));
        log.debug("Startup record inserted into app_startup_log.");
    }

    private static String getHostname() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /** Minimal SQL string escaping — single quotes only. Not for user input. */
    private static String escapeString(String value) {
        if (value == null) return "";
        return value.replace("'", "''");
    }
}
