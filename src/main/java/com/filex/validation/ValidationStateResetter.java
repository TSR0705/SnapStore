package com.filex.validation;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.filex.alert.AlertEngine;
import com.filex.alert.IncidentPersistenceSubscriber;
import com.filex.detection.DetectionEngine;
import com.filex.engine.MonitoringEngine;

/**
 * Performs the gated, startup-only validation reset of persistent and
 * in-memory state required by {@code TruthValidationCoordinator}.
 *
 * <p>The database reset truncates the three incident-related tables in
 * a single transaction with foreign keys enabled, in the deletion order
 * {@code forensic_timeline -> incident_evidence -> incidents} (matches
 * the schema's mixed ON DELETE CASCADE / SET NULL contract).
 *
 * <p>The engine reset delegates to each engine's own {@code resetForValidation()}
 * method, which is responsible for its own state-machine guard
 * (each throws {@link IllegalStateException} if invoked outside IDLE/STOPPED).
 *
 * <p>This class is a final utility holder; all methods are static and
 * carry no mutable state, making them inherently thread-safe.
 */
public final class ValidationStateResetter {

    private static final Logger log = LoggerFactory.getLogger(ValidationStateResetter.class);

    private ValidationStateResetter() {
        // utility class - no instances
    }

    /**
     * Truncates the incident-related tables inside a single transaction.
     *
     * @param connection live JDBC connection to the FileX SQLite database
     * @throws SQLException if any statement, commit, or rollback fails;
     *                      the original {@link SQLException} is rethrown
     *                      after a best-effort rollback
     */
    public static void resetDatabase(Connection connection) throws SQLException {
        log.info(TruthMarkers.TRUTH,
                "component=ValidationStateResetter event=db_truncate_started");

        long startNanos = System.nanoTime();
        boolean previousAutoCommit = connection.getAutoCommit();

        try {
            connection.setAutoCommit(false);

            // Ensure foreign keys are enforced for this connection.
            try (Statement pragma = connection.createStatement()) {
                pragma.execute("PRAGMA foreign_keys = ON");
            }

            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("DELETE FROM forensic_timeline");
                stmt.executeUpdate("DELETE FROM incident_evidence");
                stmt.executeUpdate("DELETE FROM incidents");
                connection.commit();
            } catch (SQLException txError) {
                safeRollback(connection, txError);
                throw txError;
            }

            long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
            log.info(TruthMarkers.TRUTH,
                    "component=ValidationStateResetter event=db_truncated "
                            + "tables=[forensic_timeline,incident_evidence,incidents] "
                            + TruthMarkers.kv("durationMs", durationMs));
        } catch (SQLException error) {
            log.error(TruthMarkers.TRUTH,
                    "component=ValidationStateResetter event=db_truncate_failed "
                            + TruthMarkers.kv("err", error.getMessage()),
                    error);
            throw error;
        } finally {
            try {
                connection.setAutoCommit(previousAutoCommit);
            } catch (SQLException restoreError) {
                log.error(TruthMarkers.TRUTH,
                        "component=ValidationStateResetter event=autocommit_restore_failed "
                                + TruthMarkers.kv("err", restoreError.getMessage()),
                        restoreError);
            }
        }
    }

    /**
     * Resets the in-memory state of every engine that participates in
     * the truth-validation pipeline. Each engine enforces its own
     * IDLE/STOPPED gate; this method only orchestrates the call order.
     *
     * <p>Order is significant: monitoring is reset first (source of events),
     * then detection (consumer of monitoring events), then alert
     * (consumer of detections), and finally the persistence subscriber.
     *
     * @param monitoringEngine               the monitoring engine
     * @param detectionEngine                the detection engine
     * @param alertEngine                    the alert engine
     * @param incidentPersistenceSubscriber  the incident persistence subscriber
     */
    public static void resetEngines(MonitoringEngine monitoringEngine,
                                    DetectionEngine detectionEngine,
                                    AlertEngine alertEngine,
                                    IncidentPersistenceSubscriber incidentPersistenceSubscriber) {
        // MonitoringEngine doesn't need state reset - it's stateless for validation purposes
        // State is managed by stop/start lifecycle

        log.info(TruthMarkers.TRUTH,
                "component=ValidationStateResetter event=engine_reset_started "
                        + "target=DetectionEngine");
        detectionEngine.resetForValidation();
        log.info(TruthMarkers.TRUTH,
                "component=ValidationStateResetter event=engine_reset_completed "
                        + "target=DetectionEngine");

        log.info(TruthMarkers.TRUTH,
                "component=ValidationStateResetter event=engine_reset_started "
                        + "target=AlertEngine");
        alertEngine.resetForValidation();
        log.info(TruthMarkers.TRUTH,
                "component=ValidationStateResetter event=engine_reset_completed "
                        + "target=AlertEngine");

        log.info(TruthMarkers.TRUTH,
                "component=ValidationStateResetter event=engine_reset_started "
                        + "target=IncidentPersistenceSubscriber");
        incidentPersistenceSubscriber.resetForValidation();
        log.info(TruthMarkers.TRUTH,
                "component=ValidationStateResetter event=engine_reset_completed "
                        + "target=IncidentPersistenceSubscriber");
    }

    private static void safeRollback(Connection connection, SQLException cause) {
        try {
            connection.rollback();
            log.error(TruthMarkers.TRUTH,
                    "component=ValidationStateResetter event=db_truncate_rolled_back "
                            + TruthMarkers.kv("err", cause.getMessage()));
        } catch (SQLException rollbackError) {
            log.error(TruthMarkers.TRUTH,
                    "component=ValidationStateResetter event=db_truncate_rollback_failed "
                            + TruthMarkers.kv("err", rollbackError.getMessage()),
                    rollbackError);
        }
    }
}
