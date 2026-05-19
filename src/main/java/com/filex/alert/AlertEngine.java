package com.filex.alert;

import com.filex.detection.*;
import com.filex.event.EventBus;
import com.filex.validation.TruthMarkers;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Production-grade alert engine for incident correlation and management.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Subscribe to detection events from DetectionEngine
 *   <li>Correlate related detections into incidents
 *   <li>Suppress duplicate alerts
 *   <li>Manage incident lifecycle
 *   <li>Publish incident events
 *   <li>Provide alert metrics and observability
 * </ul>
 *
 * <p>Thread ownership:
 *
 * <ul>
 *   <li>AlertEngine owns incident processing executor
 *   <li>Async incident processing (does NOT block detection pipeline)
 *   <li>Named threads: "filex-alert-processor"
 * </ul>
 *
 * <p>The alert engine does NOT:
 *
 * <ul>
 *   <li>Access UI directly
 *   <li>Write SQL directly
 *   <li>Own DetectionEngine
 *   <li>Send notifications
 *   <li>Tightly couple to monitoring internals
 * </ul>
 */
public final class AlertEngine {

  private static final Logger log = LoggerFactory.getLogger(AlertEngine.class);

  private static final int PROCESSING_THREADS = 2;
  private static final int PROCESSING_QUEUE_CAPACITY = 5000;
  private static final long SHUTDOWN_TIMEOUT_SECONDS = 10;
  private static final long CLEANUP_INTERVAL_MS = 60_000; // 1 minute

  private final EventBus eventBus;
  private final CorrelationEngine correlationEngine;
  private final SuppressionEngine suppressionEngine;

  private ExecutorService processingExecutor;
  private ScheduledExecutorService cleanupExecutor;
  private BlockingQueue<DetectionEvent> processingQueue;

  private volatile AlertState state = AlertState.IDLE;

  // Active incidents (in-memory tracking)
  private final Map<String, Incident> activeIncidents = new ConcurrentHashMap<>();

  // Metrics
  private final AtomicLong totalAlertsProcessed = new AtomicLong(0);
  private final AtomicLong totalIncidentsCreated = new AtomicLong(0);
  private final AtomicLong totalIncidentsMerged = new AtomicLong(0);
  private final AtomicLong totalAlertsSuppressed = new AtomicLong(0);
  private final AtomicLong totalCorrelationFailures = new AtomicLong(0);
  private final AtomicLong totalDroppedAlerts = new AtomicLong(0);

  public AlertEngine(EventBus eventBus) {
    this(eventBus, new CorrelationEngine(), new SuppressionEngine());
  }

  public AlertEngine(
      EventBus eventBus, CorrelationEngine correlationEngine, SuppressionEngine suppressionEngine) {
    this.eventBus = Objects.requireNonNull(eventBus, "eventBus must not be null");
    this.correlationEngine =
        Objects.requireNonNull(correlationEngine, "correlationEngine must not be null");
    this.suppressionEngine =
        Objects.requireNonNull(suppressionEngine, "suppressionEngine must not be null");
    log.info("AlertEngine created");
  }

  /**
   * Starts the alert engine.
   *
   * @throws AlertException if alert engine fails to start
   */
  public synchronized void start() throws AlertException {
    if (state != AlertState.IDLE && state != AlertState.STOPPED) {
      throw new AlertException("Cannot start alert engine in state: " + state);
    }

    log.info("Starting alert engine...");
    state = AlertState.STARTING;

    try {
      // Initialize processing queue and executor
      processingQueue = new LinkedBlockingQueue<>(PROCESSING_QUEUE_CAPACITY);
      processingExecutor =
          Executors.newFixedThreadPool(
              PROCESSING_THREADS,
              r -> {
                Thread t = new Thread(r, "filex-alert-processor-" + System.nanoTime());
                t.setDaemon(false);
                return t;
              });

      // Initialize cleanup executor
      cleanupExecutor =
          Executors.newSingleThreadScheduledExecutor(
              r -> {
                Thread t = new Thread(r, "filex-alert-cleanup");
                t.setDaemon(false);
                return t;
              });

      // Subscribe to all detection event types
      subscribeToDetectionEvents();

      // Set state to RUNNING before starting workers
      state = AlertState.RUNNING;

      // Start processing workers
      for (int i = 0; i < PROCESSING_THREADS; i++) {
        processingExecutor.submit(this::processingLoop);
      }

      // Schedule periodic cleanup
      cleanupExecutor.scheduleAtFixedRate(
          this::performCleanup, CLEANUP_INTERVAL_MS, CLEANUP_INTERVAL_MS, TimeUnit.MILLISECONDS);

      log.info("Alert engine started successfully");
      int totalCapacity = processingQueue.remainingCapacity() + processingQueue.size();
      log.info(
          TruthMarkers.TRUTH,
          "component=AlertEngine event=engine_started workers={} queueCapacity={}",
          PROCESSING_THREADS,
          totalCapacity);

    } catch (Exception e) {
      state = AlertState.FAILED;
      throw new AlertException("Failed to start alert engine", e);
    }
  }

  /** Stops the alert engine and cleans up resources. */
  public synchronized void stop() {
    if (state == AlertState.STOPPED || state == AlertState.IDLE) {
      log.debug("Alert engine already stopped");
      return;
    }

    log.info("Stopping alert engine...");
    state = AlertState.STOPPING;

    try {
      // Unsubscribe from detection events
      unsubscribeFromDetectionEvents();

      // Shutdown cleanup executor
      if (cleanupExecutor != null) {
        cleanupExecutor.shutdown();
        cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS);
      }

      // Shutdown processing executor
      if (processingExecutor != null) {
        processingExecutor.shutdown();
        if (!processingExecutor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
          log.warn("Processing executor did not terminate in time, forcing shutdown");
          processingExecutor.shutdownNow();
        }
      }

      // Clear state
      activeIncidents.clear();
      correlationEngine.clear();
      suppressionEngine.clear();

      state = AlertState.STOPPED;
      log.info("Alert engine stopped successfully");

    } catch (InterruptedException e) {
      log.error("Interrupted during alert engine shutdown", e);
      state = AlertState.FAILED;
      Thread.currentThread().interrupt();
    }
  }

  /** Returns current alert engine state. */
  public AlertState getState() {
    return state;
  }

  /** Returns a snapshot of alert metrics. */
  public AlertMetrics getMetrics() {
    return new AlertMetrics(
        totalAlertsProcessed.get(),
        totalIncidentsCreated.get(),
        totalIncidentsMerged.get(),
        totalAlertsSuppressed.get(),
        totalCorrelationFailures.get(),
        totalDroppedAlerts.get(),
        activeIncidents.size(),
        processingQueue != null ? processingQueue.size() : 0,
        state);
  }

  // -------------------------------------------------------------------------
  // Private helpers
  // -------------------------------------------------------------------------

  private void subscribeToDetectionEvents() {
    eventBus.subscribe(MassDeletionDetectedEvent.class, this::onDetectionEvent);
    eventBus.subscribe(RapidModificationDetectedEvent.class, this::onDetectionEvent);
    eventBus.subscribe(SuspiciousRenameDetectedEvent.class, this::onDetectionEvent);
    eventBus.subscribe(HiddenFileDetectedEvent.class, this::onDetectionEvent);
    eventBus.subscribe(SensitiveDirectoryAccessDetectedEvent.class, this::onDetectionEvent);
  }

  private void unsubscribeFromDetectionEvents() {
    eventBus.unsubscribe(MassDeletionDetectedEvent.class, this::onDetectionEvent);
    eventBus.unsubscribe(RapidModificationDetectedEvent.class, this::onDetectionEvent);
    eventBus.unsubscribe(SuspiciousRenameDetectedEvent.class, this::onDetectionEvent);
    eventBus.unsubscribe(HiddenFileDetectedEvent.class, this::onDetectionEvent);
    eventBus.unsubscribe(SensitiveDirectoryAccessDetectedEvent.class, this::onDetectionEvent);
  }

  private void onDetectionEvent(DetectionEvent detection) {
    if (state != AlertState.RUNNING) {
      return;
    }

    log.info(
        TruthMarkers.TRUTH,
        "TRUTH stage=Alert action=event_received detectionId={} rule={} severity={}",
        san(detection.eventId()),
        san(detection.ruleName()),
        san(detection.severity()));

    // Queue for processing (non-blocking)
    boolean queued = processingQueue.offer(detection);
    if (!queued) {
      totalDroppedAlerts.incrementAndGet();
      log.warn("Processing queue full, dropping detection: {}", detection.ruleName());
      log.warn(
          TruthMarkers.TRUTH,
          "component=AlertEngine event=alert_queue_full detectionId={}",
          san(detection.eventId()));
    }
  }

  private void processingLoop() {
    log.debug("Alert processing thread started: {}", Thread.currentThread().getName());
    log.info(
        TruthMarkers.TRUTH,
        "component=AlertEngine event=worker_started thread={}",
        san(Thread.currentThread().getName()));

    while (state == AlertState.RUNNING) {
      try {
        DetectionEvent detection = processingQueue.poll(100, TimeUnit.MILLISECONDS);
        if (detection != null) {
          // Propagate MDC validation run identifier across worker thread boundaries
          String correlationId = detection.correlationId();
          try {
            if (correlationId != null) {
              org.slf4j.MDC.put(
                  com.filex.validation.TruthValidationCoordinator.MDC_VALIDATION_RUN_ID,
                  correlationId);
            }
            processDetection(detection);
          } finally {
            org.slf4j.MDC.remove(
                com.filex.validation.TruthValidationCoordinator.MDC_VALIDATION_RUN_ID);
          }
        }
      } catch (InterruptedException e) {
        log.debug("Processing thread interrupted");
        Thread.currentThread().interrupt();
        break;
      } catch (Exception e) {
        log.error("Unexpected error in processing loop", e);
      }
    }

    log.debug("Alert processing thread stopped: {}", Thread.currentThread().getName());
  }

  private void processDetection(DetectionEvent detection) {
    totalAlertsProcessed.incrementAndGet();

    log.debug(
        TruthMarkers.TRUTH,
        "component=AlertEngine event=detection_received detectionId={} rule={} severity={}",
        san(detection.eventId()),
        san(detection.ruleName()),
        san(detection.severity()));

    try {
      long correlationLatencyMs =
          java.time.Duration.between(detection.occurredAt(), java.time.Instant.now()).toMillis();

      // Check suppression first
      if (suppressionEngine.shouldSuppress(detection)) {
        totalAlertsSuppressed.incrementAndGet();
        log.debug("Alert suppressed: {}", detection.ruleName());
        log.info(
            TruthMarkers.TRUTH,
            "TRUTH stage=Alert action=detection_evaluated detectionId={} result=suppressed reason=duplicate correlationLatencyMs={}",
            san(detection.eventId()),
            correlationLatencyMs);
        log.debug(
            TruthMarkers.TRUTH,
            "component=AlertEngine event=detection_suppressed detectionId={} reason=duplicate",
            san(detection.eventId()));
        return;
      }

      // Check correlation
      String correlatedIncidentId = correlationEngine.findCorrelatedIncident(detection);

      if (correlatedIncidentId != null) {
        log.info(
            TruthMarkers.TRUTH,
            "TRUTH stage=Alert action=correlation_decision detectionId={} result=merge incidentId={} correlationLatencyMs={}",
            san(detection.eventId()),
            san(correlatedIncidentId),
            correlationLatencyMs);
        log.debug(
            TruthMarkers.TRUTH,
            "component=AlertEngine event=correlation_decision detectionId={} outcome=match incidentId={}",
            san(detection.eventId()),
            san(correlatedIncidentId));
        // Merge with existing incident
        mergeDetectionIntoIncident(correlatedIncidentId, detection);
      } else {
        log.info(
            TruthMarkers.TRUTH,
            "TRUTH stage=Alert action=correlation_decision detectionId={} result=create correlationLatencyMs={}",
            san(detection.eventId()),
            correlationLatencyMs);
        log.debug(
            TruthMarkers.TRUTH,
            "component=AlertEngine event=correlation_decision detectionId={} outcome=new",
            san(detection.eventId()));
        // Create new incident
        createIncidentFromDetection(detection);
      }

    } catch (Exception e) {
      totalCorrelationFailures.incrementAndGet();
      log.info(
          TruthMarkers.TRUTH,
          "TRUTH stage=Alert action=detection_evaluated detectionId={} result=failure err={}",
          san(detection.eventId()),
          san(e.getMessage()));
      log.error("Failed to process detection: {} - {}", detection.ruleName(), e.getMessage(), e);
      log.error(
          TruthMarkers.TRUTH,
          "component=AlertEngine event=alert_processing_failure detectionId={} err={}",
          san(detection.eventId()),
          san(e.getMessage()),
          e);
      // Continue processing other detections (failure isolation)
    }
  }

  private void createIncidentFromDetection(DetectionEvent detection) {
    String incidentId = UUID.randomUUID().toString();
    Instant now = Instant.now();

    // Map detection severity to incident severity
    IncidentSeverity incidentSeverity = mapSeverity(detection.severity());

    Incident incident =
        Incident.builder()
            .incidentId(incidentId)
            .severity(incidentSeverity)
            .confidence(detection.confidence())
            .status(IncidentStatus.OPEN)
            .title(generateIncidentTitle(detection))
            .description(detection.description())
            .createdAt(now)
            .updatedAt(now)
            .lastSeenAt(now)
            .addLinkedDetectionId(detection.eventId())
            .correlationId(generateCorrelationId(detection))
            .evidenceSummary(buildEvidenceSummary(detection))
            .escalationLevel(0)
            .detectionCount(1)
            .build();

    activeIncidents.put(incidentId, incident);
    correlationEngine.registerIncident(detection, incidentId);
    totalIncidentsCreated.incrementAndGet();

    log.info("Created incident: {} - {}", incidentId, incident.getTitle());
    log.info(
        TruthMarkers.TRUTH,
        "TRUTH stage=Alert action=incident_created incidentId={} rule={} severity={} correlationId={} detectionId={} result=success",
        san(incident.getIncidentId()),
        san(detection.ruleName()),
        san(incidentSeverity),
        san(incident.getCorrelationId()),
        san(detection.eventId()));
    log.info(
        TruthMarkers.TRUTH,
        "component=AlertEngine event=incident_created incidentId={} rule={} severity={} correlationId={} detectionId={}",
        san(incident.getIncidentId()),
        san(detection.ruleName()),
        san(incidentSeverity),
        san(incident.getCorrelationId()),
        san(detection.eventId()));

    // Publish incident created event
    eventBus.tryPublishAsync(new IncidentCreatedEvent(incident));
  }

  private void mergeDetectionIntoIncident(String incidentId, DetectionEvent detection) {
    Incident existingIncident = activeIncidents.get(incidentId);
    if (existingIncident == null) {
      log.warn("Correlated incident not found: {}, creating new incident", incidentId);
      createIncidentFromDetection(detection);
      return;
    }

    Instant now = Instant.now();

    // Capture pre-modification severity for instrumentation
    IncidentSeverity oldSeverity = existingIncident.getSeverity();

    // Build updated incident
    Incident.Builder builder =
        existingIncident.toBuilder()
            .updatedAt(now)
            .lastSeenAt(now)
            .addLinkedDetectionId(detection.eventId())
            .detectionCount(existingIncident.getDetectionCount() + 1);

    // Escalate severity if needed
    IncidentSeverity newSeverity =
        IncidentSeverity.max(existingIncident.getSeverity(), mapSeverity(detection.severity()));

    if (newSeverity != existingIncident.getSeverity()) {
      builder.severity(newSeverity);
      builder.escalationLevel(existingIncident.getEscalationLevel() + 1);
      log.info(
          "Escalated incident {} severity: {} → {}",
          incidentId,
          existingIncident.getSeverity(),
          newSeverity);
    }

    // Escalate confidence if needed
    Confidence newConfidence =
        Confidence.max(existingIncident.getConfidence(), detection.confidence());
    builder.confidence(newConfidence);

    Incident updatedIncident = builder.build();
    activeIncidents.put(incidentId, updatedIncident);
    correlationEngine.updateCorrelation(detection);
    totalIncidentsMerged.incrementAndGet();

    log.info(
        "Merged detection into incident: {} (now {} detections)",
        incidentId,
        updatedIncident.getDetectionCount());

    String updateReason = "Detection merged: " + detection.ruleName();
    boolean escalated = newSeverity != oldSeverity;
    log.info(
        TruthMarkers.TRUTH,
        "TRUTH stage=Alert action=incident_merged incidentId={} oldSeverity={} newSeverity={} escalated={} detectionCount={} detectionId={} result=success",
        san(updatedIncident.getIncidentId()),
        san(oldSeverity),
        san(newSeverity),
        escalated,
        updatedIncident.getDetectionCount(),
        san(detection.eventId()));
    log.info(
        TruthMarkers.TRUTH,
        "component=AlertEngine event=incident_updated incidentId={} reason={} oldSeverity={} newSeverity={} escalated={} detectionCount={} detectionId={}",
        san(updatedIncident.getIncidentId()),
        san(updateReason),
        san(oldSeverity),
        san(newSeverity),
        escalated,
        updatedIncident.getDetectionCount(),
        san(detection.eventId()));

    // Publish incident updated event
    eventBus.tryPublishAsync(new IncidentUpdatedEvent(updatedIncident, updateReason));
  }

  private void performCleanup() {
    try {
      log.debug("Performing periodic cleanup...");

      int correlationsRemoved = correlationEngine.cleanupExpiredCorrelations();
      int suppressionsRemoved = suppressionEngine.cleanupExpiredSuppressions();

      log.debug(
          "Cleanup complete: {} correlations, {} suppressions removed",
          correlationsRemoved,
          suppressionsRemoved);

    } catch (Exception e) {
      log.error("Error during cleanup", e);
    }
  }

  private IncidentSeverity mapSeverity(com.filex.detection.Severity detectionSeverity) {
    return switch (detectionSeverity) {
      case LOW -> IncidentSeverity.LOW;
      case MEDIUM -> IncidentSeverity.MEDIUM;
      case HIGH -> IncidentSeverity.HIGH;
      case CRITICAL -> IncidentSeverity.CRITICAL;
    };
  }

  private String generateIncidentTitle(DetectionEvent detection) {
    return detection.ruleName() + " - " + detection.severity();
  }

  private String generateCorrelationId(DetectionEvent detection) {
    return detection.ruleName() + "-" + System.currentTimeMillis();
  }

  private Map<String, Object> buildEvidenceSummary(DetectionEvent detection) {
    Map<String, Object> evidence = new HashMap<>();
    evidence.put("ruleName", detection.ruleName());
    evidence.put("severity", detection.severity().toString());
    evidence.put("confidence", detection.confidence().toString());
    evidence.put("affectedPathCount", detection.affectedPaths().size());
    if (!detection.affectedPaths().isEmpty()) {
      evidence.put("primaryPath", detection.affectedPaths().get(0).toString());
    }
    return evidence;
  }

  /**
   * Sanitizes a value for inline structured-log emission by stripping newlines/tabs/carriage
   * returns that would corrupt key=value framing.
   */
  private static String san(Object v) {
    if (v == null) {
      return "null";
    }
    String s = v.toString();
    return s.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
  }

  /**
   * Validation-mode reset. Clears in-memory engine state so that a fresh truth-validation run
   * starts from a clean slate. Must only be invoked before the engine is started, or after it has
   * been stopped.
   *
   * @throws IllegalStateException if the engine is not in IDLE or STOPPED.
   */
  public synchronized void resetForValidation() {
    if (state != AlertState.IDLE && state != AlertState.STOPPED) {
      throw new IllegalStateException(
          "AlertEngine.resetForValidation() requires IDLE/STOPPED, current=" + state);
    }
    activeIncidents.clear();
    correlationEngine.clear();
    suppressionEngine.clear();
    if (processingQueue != null) {
      processingQueue.clear();
    }
    totalAlertsProcessed.set(0);
    totalIncidentsCreated.set(0);
    totalIncidentsMerged.set(0);
    totalAlertsSuppressed.set(0);
    totalCorrelationFailures.set(0);
    totalDroppedAlerts.set(0);
    log.info(TruthMarkers.TRUTH, "component=AlertEngine event=reset_for_validation_complete");
  }
}
