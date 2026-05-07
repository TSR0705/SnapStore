package com.filex.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages database schema migrations.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Track applied migrations in {@code schema_version} table</li>
 *   <li>Execute pending migrations in version order</li>
 *   <li>Fail fast if any migration fails</li>
 *   <li>Log migration execution</li>
 * </ul>
 *
 * <p>Migrations are registered explicitly and executed at startup.
 * This ensures the database schema is always up-to-date before the
 * application begins normal operation.
 */
public final class MigrationManager {

    private static final Logger log = LoggerFactory.getLogger(MigrationManager.class);

    private final Connection connection;
    private final List<Migration> migrations = new ArrayList<>();

    public MigrationManager(Connection connection) {
        this.connection = connection;
    }

    /**
     * Registers a migration to be executed.
     * Migrations should be registered in version order for clarity,
     * but the manager will sort them before execution.
     */
    public void register(Migration migration) {
        migrations.add(migration);
    }

    /**
     * Executes all pending migrations.
     *
     * @throws MigrationException if any migration fails
     */
    public void migrate() {
        log.info("Starting database migration...");

        try {
            ensureSchemaVersionTable();
            Set<Integer> appliedVersions = getAppliedVersions();

            // Sort migrations by version
            migrations.sort(Comparator.comparingInt(Migration::version));

            int executedCount = 0;
            for (Migration migration : migrations) {
                if (appliedVersions.contains(migration.version())) {
                    log.debug("Migration v{} already applied: {}", migration.version(), migration.description());
                    continue;
                }

                log.info("Executing migration v{}: {}", migration.version(), migration.description());
                executeMigration(migration);
                recordMigration(migration);
                executedCount++;
            }

            if (executedCount == 0) {
                log.info("Database schema is up-to-date. No migrations executed.");
            } else {
                log.info("Migration complete. Executed {} migration(s).", executedCount);
            }

        } catch (SQLException e) {
            throw new MigrationException("Database migration failed: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void ensureSchemaVersionTable() throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS schema_version (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    version     INTEGER NOT NULL UNIQUE,
                    description TEXT    NOT NULL,
                    applied_at  INTEGER NOT NULL DEFAULT (strftime('%s', 'now') * 1000)
                );
                """;

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            log.debug("schema_version table ensured.");
        }
    }

    private Set<Integer> getAppliedVersions() throws SQLException {
        Set<Integer> versions = new HashSet<>();
        String sql = "SELECT version FROM schema_version";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                versions.add(rs.getInt("version"));
            }
        }

        log.debug("Found {} applied migration(s).", versions.size());
        return versions;
    }

    private void executeMigration(Migration migration) throws SQLException {
        try {
            connection.setAutoCommit(false);
            migration.migrate(connection);
            connection.commit();
            log.info("Migration v{} executed successfully.", migration.version());
        } catch (SQLException e) {
            connection.rollback();
            log.error("Migration v{} failed. Rolling back.", migration.version(), e);
            throw new MigrationException(
                    "Migration v" + migration.version() + " failed: " + e.getMessage(), e
            );
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void recordMigration(Migration migration) throws SQLException {
        String sql = "INSERT INTO schema_version (version, description) VALUES (?, ?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, migration.version());
            pstmt.setString(2, migration.description());
            pstmt.executeUpdate();
            log.debug("Migration v{} recorded in schema_version.", migration.version());
        }
    }
}
