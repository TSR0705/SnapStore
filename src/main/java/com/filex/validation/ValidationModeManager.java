package com.filex.validation;

import com.filex.alert.AlertEngine;
import com.filex.alert.IncidentPersistenceSubscriber;
import com.filex.database.DatabaseManager;
import com.filex.detection.DetectionEngine;
import com.filex.engine.MonitoringEngine;
import com.filex.engine.MonitoringException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Coordinates validation mode reset across all FileX components.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Coordinate deterministic validation mode reset</li>
 *   <li>Stop all engines safely</li>
 *   <li>Clear validation database state</li>
 *   <li>Reset detection state (suppression, history, queues)</li>
 *   <li>Reset alert state (incidents, correlation, suppression)</li>
 *   <li>Reset UI state (incident lists, caches)</li>
 *   <li>Restart engines</li>
 * </ul>
 *
 * <p>Design principles:
 * <ul>
 *   <li><b>Explicit activation</b> — must be explicitly invoked</li>
 *   <li><b>Deterministic</b> — produces consistent clean state</li>
 *   <li><b>Safe</b> — validates preconditions before reset</li>
 *   <li><b>Thread-safe</b> — synchronized to prevent concurrent resets</li>
 *   <li><b>Non-destructive to production</b> — only affects validation mode</li>
 * </ul>
 *
 * <p>Reset sequence:
 * <ol>
 *   <li>Stop MonitoringEngine</li>
 *   <li>Stop DetectionEngine</li>
 *   <li>Stop AlertEngine</li>
 *   <li>Stop IncidentPersistenceSubscriber</li>
 *   <li>Clear validation database (incidents, evidence, timeline)</li>
 *   <li>Reset DetectionEngine state (suppression, history, queues)</li>
 *   <li>Reset AlertEngine state (incidents, correlation, suppression)</li>
 *   <li>Reset IncidentPersistenceSubscriber state (recent detections)</li>
 *   <li>Reset SystemReadinessCoordinator signal</li>
 *   <li>Restart IncidentPersistenceSubscriber</li>
 *   <li>Restart AlertEngine</li>
 *   <li>Restart DetectionEngine</li>
 *   <li>Restart MonitoringEngine</li>
 * </ol>
 *
 * <p>Thread-safety: All methods are synchronized.
 */
public final class ValidationModeManager {

    private static final Logger log = LoggerFactory.getLogger(ValidationModeManager.class);

    private final MonitoringEngine monitoringEngine;
    private final DetectionEngine detectionEngine;
    private final AlertEngine alertEngine;
    private final IncidentPersistenceSubscriber persistenceSubscriber;
    private final DatabaseManager databaseManager;
    private final SystemReadinessCoordinator readinessCoordinator;

    private final AtomicBoolean validationModeActive = new AtomicBoolean(false);

    // Cached monitoring paths for restart
    private volatile List<Path> monitoringPaths;

    public ValidationModeManager(
            MonitoringEngine monitoringEngine,
            DetectionEngine detectionEngine,
            AlertEngine alertEngine,
            IncidentPersistenceSubscriber persistenceSubscriber,
            DatabaseManager databaseManager,
            SystemReadinessCoordinator readinessCoordinator) {

        this.monitoringEngine = Objects.requireNonNull(monitoringEngine, "monitoringEngine must not be null");
        this.detectionEngine = Objects.requireNonNull(detectionEngine, "detectionEngine must not be null");
        this.alertEngine = Objects.requireNonNull(alertEngine, "alertEngine must not be null");
        this.persistenceSubscriber = Objects.requireNonNull(persistenceSubscriber, "persistenceSubscriber must not be null");
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager must not be null");
        this.readinessCoordinator = Objects.requireNonNull(readinessCoordinator, "readinessCoordinator must not be null");
    }

    /**
     * Activates validation mode.
     *
     * <p>This must be called before {@link #resetForValidation()} to enable
     * validation mode operations.
     */
    public synchronized void activateValidationMode() {
        if (validationModeActive.compareAndSet(false, true)) {
            log.info(TruthMarkers.TRUTH,
                    "component=ValidationModeManager event=validation_mode_activated");
            log.info("Validation mode activated. Reset operations are now enabled.");
        } else {
            log.debug("Validation mode already active");
        }
    }

    /**
     * Deactivates validation mode.
     */
    public synchronized void deactivateValidationMode() {
        if (validationModeActive.compareAndSet(true, false)) {
            log.info(TruthMarkers.TRUTH,
                    "component=ValidationModeManager event=validation_mode_deactivated");
            log.info("Validation mode deactivated. Reset operations are now disabled.");
        } else {
            log.debug("Validation mode already inactive");
        }
    }

    /**
     * Returns whether validation mode is currently active.
     */
    public boolean isValidationModeActive() {
        return validationModeActive.get();
    }

    /**
     * Performs a complete validation mode reset.
     *
     * <p>This method stops all engines, clears all state, and restarts engines.
     * It should only be called when validation mode is active.
     *
     * @param monitoringPaths the paths to monitor after restart
     * @throws ValidationResetException if reset fails
     */
    public synchronized void resetForValidation(List<Path> monitoringPaths) throws ValidationResetException {
        if (!validationModeActive.get()) {
            throw new ValidationResetException("Validation mode is not active. Call activateValidationMode() first.");
        }

        log.info(TruthMarkers.TRUTH,
                "component=ValidationModeManager event=validation_reset_started");
        log.info("=".repeat(80));
        log.info("VALIDATION MODE RESET STARTED");
        log.info("Stopping all engines and clearing state...");
        log.info("=".repeat(80));

        this.monitoringPaths = monitoringPaths;

        try {
            // Step 1: Stop all engines
            stopAllEngines();

            // Step 2: Clear database state
            clearDatabaseState();

            // Step 3: Reset engine states
            resetEngineStates();

            // Step 4: Reset readiness coordinator
            readinessCoordinator.resetReadinessSignal();

            // Step 5: Restart all engines
            restartAllEngines();

            log.info(TruthMarkers.TRUTH,
                    "component=ValidationModeManager event=validation_reset_completed");
            log.info("=".repeat(80));
            log.info("VALIDATION MODE RESET COMPLETED");
            log.info("All engines restarted. System ready for fresh validation run.");
            log.info("=".repeat(80));

        } catch (Exception e) {
            log.error(TruthMarkers.TRUTH,
                    "component=ValidationModeManager event=validation_reset_failed err={}",
                    sanitize(e.getMessage()), e);
            log.error("Validation mode reset failed", e);
            throw new ValidationResetException("Validation reset failed", e);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void stopAllEngines() {
        log.info(TruthMarkers.TRUTH,
                "component=ValidationModeManager event=stopping_engines");
        log.info("Stopping all engines...");

        // Stop in reverse dependency order
        monitoringEngine.stop();
        log.debug("MonitoringEngine stopped");

        detectionEngine.stop();
        log.debug("DetectionEngine stopped");

        alertEngine.stop();
        log.debug("AlertEngine stopped");

        persistenceSubscriber.stop();
        log.debug("IncidentPersistenceSubscriber stopped");

        log.info(TruthMarkers.TRUTH,
                "component=ValidationModeManager event=engines_stopped");
        log.info("All engines stopped successfully");
    }

    private void clearDatabaseState() throws SQLException {
        log.info(TruthMarkers.TRUTH,
                "component=ValidationModeManager event=clearing_database");
        log.info("Clearing validation database state...");

        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement()) {

            // Clear in dependency order (foreign keys)
            int timelineDeleted = stmt.executeUpdate("DELETE FROM forensic_timeline");
            log.debug(TruthMarkers.TRUTH,
                    "component=ValidationModeManager event=table_cleared table=forensic_timeline rows={}",
                    timelineDeleted);

            int evidenceDeleted = stmt.executeUpdate("DELETE FROM incident_evidence");
            log.debug(TruthMarkers.TRUTH,
                    "component=ValidationModeManager event=table_cleared table=incident_evidence rows={}",
                    evidenceDeleted);

            int incidentsDeleted = stmt.executeUpdate("DELETE FROM incidents");
            log.debug(TruthMarkers.TRUTH,
                    "component=ValidationModeManager event=table_cleared table=incidents rows={}",
                    incidentsDeleted);

            conn.commit();

            log.info(TruthMarkers.TRUTH,
                    "component=ValidationModeManager event=database_cleared " +
                    "incidents={} evidence={} timeline={}",
                    incidentsDeleted, evidenceDeleted, timelineDeleted);
            log.info("Database cleared: {} incidents, {} evidence, {} timeline events",
                    incidentsDeleted, evidenceDeleted, timelineDeleted);

        } catch (SQLException e) {
            log.error(TruthMarkers.TRUTH,
                    "component=ValidationModeManager event=database_clear_failed err={}",
                    sanitize(e.getMessage()), e);
            throw e;
        }
    }

    private void resetEngineStates() {
        log.info(TruthMarkers.TRUTH,
                "component=ValidationModeManager event=resetting_engine_states");
        log.info("Resetting engine states...");

        // Reset detection engine state (suppression, history, queues)
        detectionEngine.resetForValidation();
        log.debug(TruthMarkers.TRUTH,
                "component=ValidationModeManager event=engine_state_reset engine=DetectionEngine");

        // Reset alert engine state (incidents, correlation, suppression)
        alertEngine.resetForValidation();
        log.debug(TruthMarkers.TRUTH,
                "component=ValidationModeManager event=engine_state_reset engine=AlertEngine");

        // Reset persistence subscriber state (recent detections)
        persistenceSubscriber.resetForValidation();
        log.debug(TruthMarkers.TRUTH,
                "component=ValidationModeManager event=engine_state_reset engine=IncidentPersistenceSubscriber");

        log.info(TruthMarkers.TRUTH,
                "component=ValidationModeManager event=engine_states_reset");
        log.info("All engine states reset successfully");
    }

    private void restartAllEngines() throws ValidationResetException {
        log.info(TruthMarkers.TRUTH,
                "component=ValidationModeManager event=restarting_engines");
        log.info("Restarting all engines...");

        try {
            // Restart in dependency order
            persistenceSubscriber.start();
            log.debug("IncidentPersistenceSubscriber restarted");

            alertEngine.start();
            log.debug("AlertEngine restarted");

            detectionEngine.start();
            log.debug("DetectionEngine restarted");

            monitoringEngine.start(monitoringPaths);
            log.debug("MonitoringEngine restarted");

            log.info(TruthMarkers.TRUTH,
                    "component=ValidationModeManager event=engines_restarted");
            log.info("All engines restarted successfully");

            // Check readiness
            boolean ready = readinessCoordinator.checkAndSignalReadiness();
            if (!ready) {
                log.warn("System not ready after restart. Check component states.");
            }
        } catch (Exception e) {
            log.error(TruthMarkers.TRUTH,
                    "component=ValidationModeManager event=engine_restart_failed err={}",
                    sanitize(e.getMessage()), e);
            throw new ValidationResetException("Failed to restart engines", e);
        }
    }

    /**
     * Sanitizes a value for structured logging.
     */
    private static String sanitize(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isWhitespace(c) || Character.isISOControl(c)) {
                sb.append('_');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Exception thrown when validation reset fails.
     */
    public static final class ValidationResetException extends Exception {
        public ValidationResetException(String message) {
            super(message);
        }

        public ValidationResetException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
