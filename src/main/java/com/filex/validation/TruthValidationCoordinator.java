package com.filex.validation;

import java.sql.SQLException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.filex.alert.AlertEngine;
import com.filex.alert.IncidentPersistenceSubscriber;
import com.filex.config.AppConfig;
import com.filex.database.DatabaseManager;
import com.filex.detection.DetectionEngine;
import com.filex.engine.MonitoringEngine;
import com.filex.event.EventBus;

/**
 * Orchestrates the pre-start validation reset for the FileX truth-validation
 * mode. Active only when {@link AppConfig#validationMode()} is {@code true}.
 *
 * <p>The coordinator generates a single {@code validationRunId} per run,
 * pushes it into the SLF4J {@link MDC} so every subsequent truth log carries
 * it, truncates the incident-related tables, and resets all engines'
 * in-memory state. After it returns, {@code Bootstrap} hands the same id to
 * {@link ValidationReadinessGate} which emits the authoritative ready line.
 *
 * <p>The MDC entry is intentionally <b>not</b> cleared here — it must persist
 * for the remainder of the process so downstream logs from runtime threads
 * (filesystem watcher, detection workers, alert workers, persistence) all
 * inherit the same correlation id.
 *
 * <p>All methods are static and stateless, so the class is thread-safe.
 */
public final class TruthValidationCoordinator {

    private static final Logger log = LoggerFactory.getLogger(TruthValidationCoordinator.class);

    /** SLF4J MDC key used for the per-run validation correlation id. */
    public static final String MDC_VALIDATION_RUN_ID = "validationRunId";

    private TruthValidationCoordinator() {
        // utility class - no instances
    }

    /**
     * Performs the gated, startup-only validation reset.
     *
     * @param config                         resolved app configuration
     * @param databaseManager                supplies the live JDBC connection
     * @param eventBus                       reserved for future readiness coordination
     * @param monitoringEngine               monitoring engine (must be IDLE)
     * @param detectionEngine                detection engine (must be IDLE)
     * @param alertEngine                    alert engine (must be IDLE)
     * @param incidentPersistenceSubscriber  incident persistence subscriber
     * @return the freshly generated {@code validationRunId} (also installed in MDC)
     * @throws IllegalStateException if {@code config.validationMode()} is false
     * @throws SQLException          if the database truncate fails
     */
    public static String runPreStartReset(
            AppConfig config,
            DatabaseManager databaseManager,
            EventBus eventBus,
            MonitoringEngine monitoringEngine,
            DetectionEngine detectionEngine,
            AlertEngine alertEngine,
            IncidentPersistenceSubscriber incidentPersistenceSubscriber
    ) throws SQLException {
        if (!config.validationMode()) {
            throw new IllegalStateException("validation mode not enabled");
        }

        String validationRunId = UUID.randomUUID().toString();
        MDC.put(MDC_VALIDATION_RUN_ID, validationRunId);

        log.info(TruthMarkers.TRUTH,
                "component=TruthValidationCoordinator event=validation_mode_activated "
                        + TruthMarkers.kv("validationRunId", validationRunId) + " "
                        + TruthMarkers.kv("dbPath", config.databaseFile()));

        try {
            ValidationStateResetter.resetDatabase(databaseManager.getConnection());
            ValidationStateResetter.resetEngines(
                    monitoringEngine,
                    detectionEngine,
                    alertEngine,
                    incidentPersistenceSubscriber);

            log.info(TruthMarkers.TRUTH,
                    "component=TruthValidationCoordinator event=pre_start_reset_complete "
                            + TruthMarkers.kv("validationRunId", validationRunId));

            return validationRunId;
        } catch (SQLException sqlError) {
            log.error(TruthMarkers.TRUTH,
                    "component=TruthValidationCoordinator event=pre_start_reset_failed "
                            + TruthMarkers.kv("validationRunId", validationRunId) + " "
                            + TruthMarkers.kv("err", sqlError.getMessage()),
                    sqlError);
            throw sqlError;
        } catch (RuntimeException runtimeError) {
            log.error(TruthMarkers.TRUTH,
                    "component=TruthValidationCoordinator event=pre_start_reset_failed "
                            + TruthMarkers.kv("validationRunId", validationRunId) + " "
                            + TruthMarkers.kv("err", runtimeError.getMessage()),
                    runtimeError);
            throw runtimeError;
        }
    }
}
