package com.filex.validation;

import com.filex.alert.AlertEngine;
import com.filex.alert.IncidentPersistenceSubscriber;
import com.filex.detection.DetectionEngine;
import com.filex.engine.MonitoringEngine;
import com.filex.engine.MonitoringState;
import com.filex.event.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Coordinates system readiness detection and emits authoritative readiness signal.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Track component readiness states</li>
 *   <li>Emit single "FILEX TRUTH VALIDATION READY" log when all components ready</li>
 *   <li>Prevent duplicate readiness signals</li>
 *   <li>Provide explicit readiness check API</li>
 * </ul>
 *
 * <p>Design principles:
 * <ul>
 *   <li><b>Single authoritative signal</b> — only one READY log per startup</li>
 *   <li><b>All-or-nothing</b> — all components must be ready</li>
 *   <li><b>Thread-safe</b> — can be called from multiple threads</li>
 *   <li><b>No false positives</b> — strict validation of component states</li>
 * </ul>
 *
 * <p>Readiness criteria:
 * <ul>
 *   <li>MonitoringEngine: state == RUNNING</li>
 *   <li>DetectionEngine: state == RUNNING</li>
 *   <li>AlertEngine: state == RUNNING</li>
 *   <li>IncidentPersistenceSubscriber: started == true</li>
 *   <li>EventBus: not shutdown</li>
 * </ul>
 *
 * <p>Thread-safety: All methods are thread-safe.
 */
public final class SystemReadinessCoordinator {

    private static final Logger log = LoggerFactory.getLogger(SystemReadinessCoordinator.class);

    private final MonitoringEngine monitoringEngine;
    private final DetectionEngine detectionEngine;
    private final AlertEngine alertEngine;
    private final IncidentPersistenceSubscriber persistenceSubscriber;
    private final EventBus eventBus;

    private final AtomicBoolean readinessSignalEmitted = new AtomicBoolean(false);

    public SystemReadinessCoordinator(
            MonitoringEngine monitoringEngine,
            DetectionEngine detectionEngine,
            AlertEngine alertEngine,
            IncidentPersistenceSubscriber persistenceSubscriber,
            EventBus eventBus) {

        this.monitoringEngine = Objects.requireNonNull(monitoringEngine, "monitoringEngine must not be null");
        this.detectionEngine = Objects.requireNonNull(detectionEngine, "detectionEngine must not be null");
        this.alertEngine = Objects.requireNonNull(alertEngine, "alertEngine must not be null");
        this.persistenceSubscriber = Objects.requireNonNull(persistenceSubscriber, "persistenceSubscriber must not be null");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus must not be null");
    }

    /**
     * Checks if all components are ready and emits readiness signal if so.
     *
     * <p>This method is idempotent — it will only emit the readiness signal once.
     * Subsequent calls will check readiness but not emit duplicate signals.
     *
     * @return true if system is ready (or was already ready), false otherwise
     */
    public boolean checkAndSignalReadiness() {
        boolean ready = isSystemReady();

        if (ready && readinessSignalEmitted.compareAndSet(false, true)) {
            // First time ready — emit authoritative signal
            log.info(TruthMarkers.TRUTH,
                    "component=SystemReadinessCoordinator event=FILEX_TRUTH_VALIDATION_READY " +
                    "monitoringState={} detectionState={} alertState={} persistenceStarted={} eventBusActive={}",
                    sanitize(monitoringEngine.getState().name()),
                    sanitize(detectionEngine.getState().name()),
                    sanitize(alertEngine.getState().name()),
                    persistenceSubscriber.isStarted(),
                    !eventBus.getMetrics().isShutdown());

            log.info("=".repeat(80));
            log.info("FILEX TRUTH VALIDATION READY");
            log.info("All components operational. System ready for end-to-end validation.");
            log.info("=".repeat(80));
        }

        return ready;
    }

    /**
     * Checks if the system is ready without emitting a signal.
     *
     * @return true if all components are ready, false otherwise
     */
    public boolean isSystemReady() {
        boolean monitoringReady = monitoringEngine.getState() == MonitoringState.RUNNING;
        boolean detectionReady = detectionEngine.getState() == com.filex.detection.DetectionState.RUNNING;
        boolean alertReady = alertEngine.getState() == com.filex.alert.AlertState.RUNNING;
        boolean persistenceReady = persistenceSubscriber.isStarted();
        boolean eventBusReady = !eventBus.getMetrics().isShutdown();

        boolean allReady = monitoringReady && detectionReady && alertReady && persistenceReady && eventBusReady;

        if (!allReady) {
            log.debug("System not ready: monitoring={} detection={} alert={} persistence={} eventBus={}",
                    monitoringReady, detectionReady, alertReady, persistenceReady, eventBusReady);
            log.debug(TruthMarkers.TRUTH,
                    "component=SystemReadinessCoordinator event=readiness_check_failed " +
                    "monitoringReady={} detectionReady={} alertReady={} persistenceReady={} eventBusReady={}",
                    monitoringReady, detectionReady, alertReady, persistenceReady, eventBusReady);
        }

        return allReady;
    }

    /**
     * Resets the readiness signal flag.
     *
     * <p>This should only be called by {@code ValidationModeManager} during
     * validation mode reset. Allows the system to emit a new readiness signal
     * after restart.
     */
    public void resetReadinessSignal() {
        readinessSignalEmitted.set(false);
        log.info(TruthMarkers.TRUTH,
                "component=SystemReadinessCoordinator event=readiness_signal_reset");
        log.info("Readiness signal reset. System can emit new READY signal after restart.");
    }

    /**
     * Returns whether the readiness signal has been emitted.
     */
    public boolean hasEmittedReadinessSignal() {
        return readinessSignalEmitted.get();
    }

    /**
     * Returns a snapshot of component readiness states.
     */
    public ReadinessSnapshot getReadinessSnapshot() {
        return new ReadinessSnapshot(
                monitoringEngine.getState() == MonitoringState.RUNNING,
                detectionEngine.getState() == com.filex.detection.DetectionState.RUNNING,
                alertEngine.getState() == com.filex.alert.AlertState.RUNNING,
                persistenceSubscriber.isStarted(),
                !eventBus.getMetrics().isShutdown(),
                readinessSignalEmitted.get()
        );
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
     * Immutable snapshot of component readiness states.
     */
    public static final class ReadinessSnapshot {
        private final boolean monitoringReady;
        private final boolean detectionReady;
        private final boolean alertReady;
        private final boolean persistenceReady;
        private final boolean eventBusReady;
        private final boolean readinessSignalEmitted;

        public ReadinessSnapshot(
                boolean monitoringReady,
                boolean detectionReady,
                boolean alertReady,
                boolean persistenceReady,
                boolean eventBusReady,
                boolean readinessSignalEmitted) {

            this.monitoringReady = monitoringReady;
            this.detectionReady = detectionReady;
            this.alertReady = alertReady;
            this.persistenceReady = persistenceReady;
            this.eventBusReady = eventBusReady;
            this.readinessSignalEmitted = readinessSignalEmitted;
        }

        public boolean isMonitoringReady() {
            return monitoringReady;
        }

        public boolean isDetectionReady() {
            return detectionReady;
        }

        public boolean isAlertReady() {
            return alertReady;
        }

        public boolean isPersistenceReady() {
            return persistenceReady;
        }

        public boolean isEventBusReady() {
            return eventBusReady;
        }

        public boolean isReadinessSignalEmitted() {
            return readinessSignalEmitted;
        }

        public boolean isSystemReady() {
            return monitoringReady && detectionReady && alertReady && persistenceReady && eventBusReady;
        }

        @Override
        public String toString() {
            return String.format(
                    "ReadinessSnapshot{monitoring=%s, detection=%s, alert=%s, persistence=%s, eventBus=%s, signalEmitted=%s}",
                    monitoringReady, detectionReady, alertReady, persistenceReady, eventBusReady, readinessSignalEmitted
            );
        }
    }
}
