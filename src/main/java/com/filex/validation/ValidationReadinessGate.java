package com.filex.validation;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.filex.alert.AlertEngine;
import com.filex.alert.AlertState;
import com.filex.alert.IncidentCreatedEvent;
import com.filex.alert.IncidentPersistenceSubscriber;
import com.filex.alert.IncidentUpdatedEvent;
import com.filex.config.AppConfig;
import com.filex.detection.DetectionEngine;
import com.filex.detection.DetectionState;
import com.filex.detection.HiddenFileDetectedEvent;
import com.filex.detection.MassDeletionDetectedEvent;
import com.filex.detection.RapidModificationDetectedEvent;
import com.filex.detection.SensitiveDirectoryAccessDetectedEvent;
import com.filex.detection.SuspiciousRenameDetectedEvent;
import com.filex.engine.MonitoringEngine;
import com.filex.engine.MonitoringState;
import com.filex.event.AppEvent;
import com.filex.event.EventBus;

/**
 * Verifies that every component required by the FileX truth-validation
 * pipeline is fully wired and running before emitting the single authoritative
 * {@code FILEX TRUTH VALIDATION READY} line.
 *
 * <p>If any precondition is unmet, an {@link IllegalStateException} is thrown
 * after logging a structured failure record. The gate never emits a false
 * ready signal.
 *
 * <p>All checks are read-only and stateless; the class is thread-safe.
 */
public final class ValidationReadinessGate {

    private static final Logger log = LoggerFactory.getLogger(ValidationReadinessGate.class);

    private ValidationReadinessGate() {
        // utility class - no instances
    }

    /**
     * Runs every readiness precondition and emits the authoritative ready
     * line on success.
     *
     * @param validationRunId                correlation id produced by
     *                                       {@link TruthValidationCoordinator}
     * @param config                         resolved app configuration
     * @param eventBus                       event bus used for subscriber-count probes
     * @param monitoringEngine               monitoring engine (must be RUNNING)
     * @param detectionEngine                detection engine (must be RUNNING)
     * @param alertEngine                    alert engine (must be RUNNING)
     * @param incidentPersistenceSubscriber  persistence subscriber (must be started)
     * @param watchRoots                     configured watch root paths
     * @param ruleNames                      registered detection rule names
     * @throws IllegalStateException if any precondition fails
     */
    public static void assertReady(
            String validationRunId,
            AppConfig config,
            EventBus eventBus,
            MonitoringEngine monitoringEngine,
            DetectionEngine detectionEngine,
            AlertEngine alertEngine,
            IncidentPersistenceSubscriber incidentPersistenceSubscriber,
            List<Path> watchRoots,
            List<String> ruleNames
    ) {
        requireEngineState("MonitoringEngine", monitoringEngine.getState(), MonitoringState.RUNNING);
        requireEngineState("DetectionEngine", detectionEngine.getState(), DetectionState.RUNNING);
        requireEngineState("AlertEngine", alertEngine.getState(), AlertState.RUNNING);

        if (!incidentPersistenceSubscriber.isStarted()) {
            failReadiness("IncidentPersistenceSubscriber",
                    "incident_persistence_subscriber_not_started");
        }

        requireSubscriber(eventBus, IncidentCreatedEvent.class);
        requireSubscriber(eventBus, IncidentUpdatedEvent.class);
        requireSubscriber(eventBus, MassDeletionDetectedEvent.class);
        requireSubscriber(eventBus, RapidModificationDetectedEvent.class);
        requireSubscriber(eventBus, SuspiciousRenameDetectedEvent.class);
        requireSubscriber(eventBus, HiddenFileDetectedEvent.class);
        requireSubscriber(eventBus, SensitiveDirectoryAccessDetectedEvent.class);

        Path dbAbsolute = config.databaseFile().toAbsolutePath();
        List<String> absoluteRoots = new ArrayList<>(
                watchRoots == null ? 0 : watchRoots.size());
        if (watchRoots != null) {
            for (Path p : watchRoots) {
                absoluteRoots.add(p.toAbsolutePath().toString());
            }
        }
        List<String> safeRuleNames = ruleNames == null ? List.of() : ruleNames;

        log.info(TruthMarkers.TRUTH,
                "FILEX TRUTH VALIDATION READY "
                        + TruthMarkers.kv("validationRunId", validationRunId) + " "
                        + TruthMarkers.kv("dbPath", dbAbsolute) + " "
                        + TruthMarkers.kvList("roots", absoluteRoots) + " "
                        + TruthMarkers.kvList("rules", safeRuleNames));
    }

    private static <S extends Enum<S>> void requireEngineState(String component,
                                                                S actual,
                                                                S expected) {
        if (actual != expected) {
            failReadiness(component,
                    "expected_state_" + expected.name() + "_actual_" + actual.name());
        }
    }

    private static void requireSubscriber(EventBus eventBus,
                                          Class<? extends AppEvent> eventType) {
        int count = eventBus.subscriberCount(eventType);
        if (count < 1) {
            failReadiness("EventBus",
                    "no_subscriber_for_" + eventType.getSimpleName()
                            + "_count=" + count);
        }
    }

    private static void failReadiness(String component, String reason) {
        log.error(TruthMarkers.TRUTH,
                "component=ValidationReadinessGate event=readiness_check_failed "
                        + TruthMarkers.kv("target", component) + " "
                        + TruthMarkers.kv("reason", reason));
        throw new IllegalStateException(
                "Validation readiness check failed: target=" + component
                        + " reason=" + reason);
    }
}
