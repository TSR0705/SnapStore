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
     * Opens the SQLite connection and bootstraps the schema via migrations.
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
            runMigrations();
            recordStartup();

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
     * Creates a new transaction template for the current connection.
     */
    public com.filex.persistence.TransactionTemplate transactionTemplate() {
        return new com.filex.persistence.TransactionTemplate(getConnection());
    }

    /**
     * Creates a new FileEventRepository instance.
     */
    public com.filex.repository.FileEventRepository fileEventRepository() {
        return new com.filex.repository.FileEventRepository(getConnection());
    }

    /**
     * Creates a new SettingsRepository instance.
     */
    public com.filex.repository.SettingsRepository settingsRepository() {
        return new com.filex.repository.SettingsRepository(getConnection());
    }

    /**
     * Creates a new AlertRepository instance.
     */
    public com.filex.repository.AlertRepository alertRepository() {
        return new com.filex.repository.AlertRepository(getConnection());
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
     * Executes database migrations to ensure schema is up-to-date.
     */
    private void runMigrations() throws SQLException {
        log.debug("Running database migrations...");

        com.filex.persistence.MigrationManager migrationManager =
                new com.filex.persistence.MigrationManager(connection);

        // Register all migrations in order
        migrationManager.register(new com.filex.persistence.migrations.V001_InitialSchema());
        migrationManager.register(new com.filex.persistence.migrations.V002_CreateIndexes());

        // Execute pending migrations
        migrationManager.migrate();

        log.info("Database migrations complete.");
    }

    /**
     * Records application startup in the audit log.
     */
    private void recordStartup() throws SQLException {
        String appVersion = System.getProperty("filex.version", "1.0.0-SNAPSHOT");
        String hostname = getHostname();
        String osName = System.getProperty("os.name", "unknown");
        String javaVer = System.getProperty("java.version", "unknown");

        String sql = "INSERT INTO app_startup_log (app_version, hostname, os_name, java_version) VALUES (?, ?, ?, ?)";
        try (var pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, appVersion);
            pstmt.setString(2, hostname);
            pstmt.setString(3, osName);
            pstmt.setString(4, javaVer);
            pstmt.executeUpdate();
            log.debug("Startup record inserted into app_startup_log.");
        }
    }

    private static String getHostname() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
