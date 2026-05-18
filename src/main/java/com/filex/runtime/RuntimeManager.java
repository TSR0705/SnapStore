package com.filex.runtime;

import com.filex.alert.AlertEngine;
import com.filex.alert.IncidentPersistenceSubscriber;
import com.filex.config.AppConfig;
import com.filex.detection.DetectionEngine;
import com.filex.engine.MonitoringEngine;
import com.filex.event.EventBus;
import com.filex.event.RuntimeState;
import com.filex.event.RuntimeStateChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Dedicated runtime lifecycle coordinator for FileX.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Own engine startup and shutdown orchestration</li>
 *   <li>Implement demo mode activation and path registration</li>
 *   <li>Track system health and operational metrics</li>
 *   <li>Provide observability into the event pipeline</li>
 * </ul>
 */
public final class RuntimeManager {

    private static final Logger log = LoggerFactory.getLogger(RuntimeManager.class);

    private final AppConfig config;
    private final EventBus eventBus;
    private final MonitoringEngine monitoringEngine;
    private final DetectionEngine detectionEngine;
    private final AlertEngine alertEngine;
    private final IncidentPersistenceSubscriber persistenceSubscriber;

    private volatile RuntimeState currentState;

    // Operational metrics
    private final AtomicLong dbWriteFailures = new AtomicLong(0);

    public RuntimeManager(
            AppConfig config,
            EventBus eventBus,
            MonitoringEngine monitoringEngine,
            DetectionEngine detectionEngine,
            AlertEngine alertEngine,
            IncidentPersistenceSubscriber persistenceSubscriber
    ) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus must not be null");
        this.monitoringEngine = Objects.requireNonNull(monitoringEngine, "monitoringEngine must not be null");
        this.detectionEngine = Objects.requireNonNull(detectionEngine, "detectionEngine must not be null");
        this.alertEngine = Objects.requireNonNull(alertEngine, "alertEngine must not be null");
        this.persistenceSubscriber = Objects.requireNonNull(persistenceSubscriber, "persistenceSubscriber must not be null");
        
        this.currentState = RuntimeState.INITIALIZING;
        log.info("RuntimeManager initialized in state: {}", currentState);
    }

    /**
     * Executes the full operational startup sequence.
     */
    public synchronized void start() {
        if (currentState != RuntimeState.INITIALIZING && currentState != RuntimeState.STOPPED) {
            log.warn("Start requested in invalid state: {}", currentState);
            return;
        }

        transitionTo(RuntimeState.STARTING);
        log.info("Executing engine startup sequence...");

        try {
            // 1. Ensure persistence subscriber is started first to catch all subsequent events
            persistenceSubscriber.start();
            log.info("Persistence subscriber activated.");

            // 2. Start Detection Engine
            detectionEngine.start();
            log.info("Detection engine activated.");

            // 3. Start Alert Engine
            alertEngine.start();
            log.info("Alert engine activated.");

            // 4. Handle Demo Mode or Monitored Path registration
            if (config.demoMode()) {
                activateDemoMode();
            } else {
                log.info("Demo mode disabled. Monitoring idle until paths are registered.");
            }

            transitionTo(RuntimeState.RUNNING);
            log.info("FileX engines started successfully.");

        } catch (Exception e) {
            log.error("Engine startup sequence failed: {}", e.getMessage(), e);
            transitionTo(RuntimeState.FAILED);
        }
    }

    /**
     * Executes the safe shutdown sequence.
     */
    public synchronized void stop() {
        if (currentState == RuntimeState.STOPPED || currentState == RuntimeState.STOPPING) {
            return;
        }

        transitionTo(RuntimeState.STOPPING);
        log.info("Executing engine shutdown sequence...");

        try {
            // 1. Stop monitoring first to stop new event intake
            monitoringEngine.stop();
            log.info("Monitoring engine stopped.");

            // 2. Stop detection and alert engines (they will drain their queues)
            detectionEngine.stop();
            log.info("Detection engine stopped.");

            alertEngine.stop();
            log.info("Alert engine stopped.");

            // 3. Stop persistence subscriber last
            persistenceSubscriber.stop();
            log.info("Persistence subscriber stopped.");

            transitionTo(RuntimeState.STOPPED);
            log.info("FileX engines stopped safely.");

        } catch (Exception e) {
            log.error("Engine shutdown sequence failed: {}", e.getMessage(), e);
            transitionTo(RuntimeState.FAILED);
        }
    }

    /**
     * Activates demo mode monitoring.
     */
    private void activateDemoMode() throws IOException, com.filex.engine.MonitoringException {
        Path demoPath = config.demoMonitorPath();
        if (demoPath == null) {
            log.error("Demo mode enabled but no monitor path configured.");
            return;
        }

        log.info("Activating demo mode for path: {}", demoPath);

        if (!Files.exists(demoPath)) {
            if (config.demoAutoCreatePath()) {
                Files.createDirectories(demoPath);
                log.info("Created demo monitor directory: {}", demoPath);
            } else {
                throw new IOException("Demo monitor path does not exist and auto-create is disabled: " + demoPath);
            }
        }

        if (!Files.isDirectory(demoPath)) {
            throw new IOException("Demo monitor path is not a directory: " + demoPath);
        }

        monitoringEngine.start(Collections.singletonList(demoPath));
        log.info("Demo monitoring started on path: {}", demoPath);
    }

    /**
     * Transitions the runtime to a new state and publishes an event.
     */
    private void transitionTo(RuntimeState newState) {
        RuntimeState oldState = this.currentState;
        this.currentState = newState;
        log.info("Runtime state transition: {} -> {}", oldState, newState);
        eventBus.publish(new RuntimeStateChangedEvent(oldState, newState));
    }

    /**
     * Returns a snapshot of the current runtime health.
     */
    public RuntimeHealth getHealth() {
        return new RuntimeHealth(
                currentState,
                monitoringEngine.getState().toString(),
                detectionEngine.getState().toString(),
                alertEngine.getState().toString(),
                persistenceSubscriber.isStarted() ? "RUNNING" : "STOPPED"
        );
    }

    /**
     * Returns a snapshot of the current runtime metrics.
     */
    public RuntimeMetrics getMetrics() {
        var mMetrics = monitoringEngine.getMetrics();
        var dMetrics = detectionEngine.getMetrics();
        var aMetrics = alertEngine.getMetrics();

        return new RuntimeMetrics(
                mMetrics.getTotalEventsDetected(),
                mMetrics.getTotalEventsNormalized(),
                dMetrics.getTotalDetections(),
                aMetrics.getTotalAlertsProcessed(),
                aMetrics.getTotalIncidentsCreated(),
                dbWriteFailures.get(),
                mMetrics.getActiveWatchCount(),
                currentState
        );
    }

    public RuntimeState getCurrentState() {
        return currentState;
    }

    public void incrementDbWriteFailures() {
        dbWriteFailures.incrementAndGet();
    }

    /** Health snapshot record. */
    public record RuntimeHealth(
            RuntimeState overallState,
            String monitoringState,
            String detectionState,
            String alertState,
            String persistenceState
    ) {}

    /** Metrics snapshot record. */
    public record RuntimeMetrics(
            long rawEventsObserved,
            long normalizedEventsPublished,
            long detectionsTriggered,
            long alertsCreated,
            long incidentsCreated,
            long dbWriteFailures,
            int activeMonitoredPaths,
            RuntimeState currentState
    ) {}
}
