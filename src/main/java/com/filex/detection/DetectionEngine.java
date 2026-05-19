package com.filex.detection;

import com.filex.engine.*;
import com.filex.event.EventBus;
import com.filex.validation.TruthMarkers;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Production-grade detection engine for identifying suspicious file activity.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Subscribe to monitoring events from EventBus
 *   <li>Evaluate events against registered detection rules
 *   <li>Maintain temporal event history for correlation
 *   <li>Publish detection events for suspicious activity
 *   <li>Provide detection metrics and observability
 * </ul>
 *
 * <p>Thread ownership:
 *
 * <ul>
 *   <li>DetectionEngine owns evaluation executor
 *   <li>Async rule evaluation (does NOT block monitoring pipeline)
 *   <li>Named threads: "filex-detection-evaluator"
 * </ul>
 *
 * <p>The detection engine does NOT:
 *
 * <ul>
 *   <li>Access UI directly
 *   <li>Write SQL directly
 *   <li>Own WatchService
 *   <li>Tightly couple to MonitoringEngine
 * </ul>
 */
public final class DetectionEngine {

  private static final Logger log = LoggerFactory.getLogger(DetectionEngine.class);

  private static final int EVALUATION_THREADS = 2;
  private static final int EVALUATION_QUEUE_CAPACITY = 5000;
  private static final long DEFAULT_EVENT_HISTORY_WINDOW_MS = 60_000; // 1 minute
  private static final int MAX_EVENT_HISTORY_SIZE = 10000;
  private static final long SHUTDOWN_TIMEOUT_SECONDS = 10;

  private final EventBus eventBus;
  private final List<DetectionRule> rules = new CopyOnWriteArrayList<>();
  private final Deque<MonitoringEvent> eventHistory = new ConcurrentLinkedDeque<>();
  private final long eventHistoryWindowMs;

  private ExecutorService evaluationExecutor;
  private BlockingQueue<MonitoringEvent> evaluationQueue;

  private volatile DetectionState state = DetectionState.IDLE;

  // Metrics
  private final AtomicLong totalEvaluations = new AtomicLong(0);
  private final AtomicLong totalDetections = new AtomicLong(0);
  private final AtomicLong totalSuppressedDetections = new AtomicLong(0);
  private final AtomicLong totalEvaluationFailures = new AtomicLong(0);
  private final AtomicLong totalFailedPublishes = new AtomicLong(0);

  // Suppression tracking (simple cooldown)
  private final Map<String, Long> lastDetectionTime = new ConcurrentHashMap<>();
  private static final long DETECTION_COOLDOWN_MS = 30_000; // 30 seconds

  public DetectionEngine(EventBus eventBus) {
    this(eventBus, DEFAULT_EVENT_HISTORY_WINDOW_MS);
  }

  public DetectionEngine(EventBus eventBus, long eventHistoryWindowMs) {
    this.eventBus = Objects.requireNonNull(eventBus, "eventBus must not be null");
    this.eventHistoryWindowMs = eventHistoryWindowMs;
    log.info("DetectionEngine created with event history window: {}ms", eventHistoryWindowMs);
  }

  /**
   * Starts the detection engine and registers default rules.
   *
   * @throws DetectionException if detection fails to start
   */
  public synchronized void start() throws DetectionException {
    if (state != DetectionState.IDLE && state != DetectionState.STOPPED) {
      throw new DetectionException("Cannot start detection in state: " + state);
    }

    log.info("Starting detection engine...");
    state = DetectionState.STARTING;

    try {
      // Initialize evaluation queue and executor
      evaluationQueue = new LinkedBlockingQueue<>(EVALUATION_QUEUE_CAPACITY);
      evaluationExecutor =
          Executors.newFixedThreadPool(
              EVALUATION_THREADS,
              r -> {
                Thread t = new Thread(r, "filex-detection-evaluator-" + System.nanoTime());
                t.setDaemon(false);
                return t;
              });

      // Subscribe to file monitoring event types (EventBus requires exact type matches)
      // Only subscribe to file events that detection rules care about
      eventBus.subscribe(RawFileCreatedEvent.class, this::onMonitoringEvent);
      eventBus.subscribe(RawFileModifiedEvent.class, this::onMonitoringEvent);
      eventBus.subscribe(RawFileDeletedEvent.class, this::onMonitoringEvent);
      eventBus.subscribe(RawDirectoryCreatedEvent.class, this::onMonitoringEvent);

      // Register default rules
      registerDefaultRules();

      String ruleNames =
          rules.stream()
              .map(DetectionRule::name)
              .map(DetectionEngine::sanitize)
              .collect(Collectors.joining(","));
      log.info(
          TruthMarkers.TRUTH,
          "component=DetectionEngine event=rules_registered count="
              + rules.size()
              + " names=["
              + ruleNames
              + "]");

      // Set state to RUNNING before starting evaluation workers
      // This prevents race condition where workers exit immediately
      state = DetectionState.RUNNING;

      int queueCapacity = evaluationQueue.remainingCapacity() + evaluationQueue.size();
      log.info(
          TruthMarkers.TRUTH,
          "component=DetectionEngine event=engine_started workers="
              + EVALUATION_THREADS
              + " queueCapacity="
              + queueCapacity);

      // Start evaluation workers (after state is RUNNING)
      for (int i = 0; i < EVALUATION_THREADS; i++) {
        evaluationExecutor.submit(this::evaluationLoop);
      }

      log.info("Detection engine started successfully with {} rules", rules.size());

    } catch (Exception e) {
      state = DetectionState.FAILED;
      throw new DetectionException("Failed to start detection engine", e);
    }
  }

  /** Stops the detection engine and cleans up resources. */
  public synchronized void stop() {
    if (state == DetectionState.STOPPED || state == DetectionState.IDLE) {
      log.debug("Detection engine already stopped");
      return;
    }

    log.info("Stopping detection engine...");
    state = DetectionState.STOPPING;

    try {
      // Unsubscribe from file monitoring event types
      eventBus.unsubscribe(RawFileCreatedEvent.class, this::onMonitoringEvent);
      eventBus.unsubscribe(RawFileModifiedEvent.class, this::onMonitoringEvent);
      eventBus.unsubscribe(RawFileDeletedEvent.class, this::onMonitoringEvent);
      eventBus.unsubscribe(RawDirectoryCreatedEvent.class, this::onMonitoringEvent);

      // Shutdown evaluation executor
      if (evaluationExecutor != null) {
        evaluationExecutor.shutdown();
        if (!evaluationExecutor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
          log.warn("Evaluation executor did not terminate in time, forcing shutdown");
          evaluationExecutor.shutdownNow();
        }
      }

      // Clear state
      eventHistory.clear();
      lastDetectionTime.clear();

      state = DetectionState.STOPPED;
      log.info("Detection engine stopped successfully");

    } catch (InterruptedException e) {
      log.error("Interrupted during detection shutdown", e);
      state = DetectionState.FAILED;
      Thread.currentThread().interrupt();
    }
  }

  /** Registers a detection rule. */
  public void registerRule(DetectionRule rule) {
    Objects.requireNonNull(rule, "rule must not be null");
    rules.add(rule);
    log.info("Registered detection rule: {}", rule.name());
  }

  /** Returns current detection state. */
  public DetectionState getState() {
    return state;
  }

  /** Returns an unmodifiable list of all registered detection rules. */
  public List<DetectionRule> getRules() {
    return Collections.unmodifiableList(rules);
  }

  /** Returns a snapshot of detection metrics. */
  public DetectionMetrics getMetrics() {
    int enabledCount = (int) rules.stream().filter(DetectionRule::isEnabled).count();

    return new DetectionMetrics(
        rules.size(),
        enabledCount,
        totalEvaluations.get(),
        totalDetections.get(),
        totalSuppressedDetections.get(),
        totalEvaluationFailures.get(),
        totalFailedPublishes.get(),
        evaluationQueue != null ? evaluationQueue.size() : 0,
        state);
  }

  /** Resets engine state for validation runs. Only callable when engine is IDLE/STOPPED. */
  public synchronized void resetForValidation() {
    if (state != DetectionState.IDLE && state != DetectionState.STOPPED) {
      throw new IllegalStateException(
          "DetectionEngine.resetForValidation() requires IDLE/STOPPED, current=" + state);
    }
    eventHistory.clear();
    lastDetectionTime.clear();
    if (evaluationQueue != null) {
      evaluationQueue.clear();
    }
    totalEvaluations.set(0);
    totalDetections.set(0);
    totalSuppressedDetections.set(0);
    totalEvaluationFailures.set(0);
    totalFailedPublishes.set(0);
    log.info(TruthMarkers.TRUTH, "component=DetectionEngine event=reset_for_validation_complete");
  }

  // -------------------------------------------------------------------------
  // Private helpers
  // -------------------------------------------------------------------------

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

  private void registerDefaultRules() {
    registerRule(new com.filex.detection.rules.MassDeletionRule());
    registerRule(new com.filex.detection.rules.RapidModificationRule());
    registerRule(new com.filex.detection.rules.SuspiciousExtensionRenameRule());
    registerRule(new com.filex.detection.rules.HiddenFileCreationRule());
    registerRule(new com.filex.detection.rules.SensitiveDirectoryActivityRule());
  }

  private void onMonitoringEvent(MonitoringEvent event) {
    if (state != DetectionState.RUNNING) {
      return;
    }

    String pathStr = (event.path() != null) ? sanitize(event.path().toString()) : "n/a";
    log.debug(
        TruthMarkers.TRUTH,
        "component=DetectionEngine event=detection_event_received type="
            + event.getClass().getSimpleName()
            + " eventId="
            + sanitize(event.eventId())
            + " path="
            + pathStr);
    log.info(
        TruthMarkers.TRUTH,
        "TRUTH stage=Detection action=event_received type={} eventId={} path={}",
        event.getClass().getSimpleName(),
        sanitize(event.eventId()),
        pathStr);

    // Add to event history
    addToEventHistory(event);

    // Queue for evaluation (non-blocking)
    boolean queued = evaluationQueue.offer(event);
    log.debug(
        TruthMarkers.TRUTH,
        "component=DetectionEngine event=detection_enqueue accepted="
            + queued
            + " depth="
            + evaluationQueue.size()
            + " eventId="
            + sanitize(event.eventId()));
    if (!queued) {
      log.warn(
          TruthMarkers.TRUTH,
          "component=DetectionEngine event=queue_overflow droppedEventId="
              + sanitize(event.eventId()));
      log.warn("Evaluation queue full, dropping event: {}", event.getClass().getSimpleName());
    } else {
      log.debug("Queued event for evaluation: {}", event.getClass().getSimpleName());
    }
  }

  private void addToEventHistory(MonitoringEvent event) {
    eventHistory.addLast(event);

    // Bounded history: remove old events
    if (eventHistory.size() > MAX_EVENT_HISTORY_SIZE) {
      eventHistory.removeFirst();
    }

    // Remove events outside time window
    long cutoffTime = System.currentTimeMillis() - eventHistoryWindowMs;
    while (!eventHistory.isEmpty()) {
      MonitoringEvent oldest = eventHistory.peekFirst();
      if (oldest != null && oldest.occurredAt().toEpochMilli() < cutoffTime) {
        eventHistory.removeFirst();
      } else {
        break;
      }
    }
  }

  private void evaluationLoop() {
    log.info(
        TruthMarkers.TRUTH,
        "component=DetectionEngine event=worker_started thread="
            + sanitize(Thread.currentThread().getName()));
    log.debug("Detection evaluation thread started: {}", Thread.currentThread().getName());

    while (state == DetectionState.RUNNING) {
      try {
        MonitoringEvent event = evaluationQueue.poll(100, TimeUnit.MILLISECONDS);
        if (event != null) {
          // Propagate MDC correlation identifier across the thread pool boundary
          String correlationId = event.correlationId();
          try {
            if (correlationId != null) {
              org.slf4j.MDC.put(
                  com.filex.validation.TruthValidationCoordinator.MDC_VALIDATION_RUN_ID,
                  correlationId);
            }
            evaluateEvent(event);
          } finally {
            org.slf4j.MDC.remove(
                com.filex.validation.TruthValidationCoordinator.MDC_VALIDATION_RUN_ID);
          }
        }
      } catch (InterruptedException e) {
        log.debug("Evaluation thread interrupted");
        Thread.currentThread().interrupt();
        break;
      } catch (Exception e) {
        log.error(
            TruthMarkers.TRUTH,
            "component=DetectionEngine event=worker_crash thread="
                + sanitize(Thread.currentThread().getName())
                + " err="
                + sanitize(String.valueOf(e.getMessage())),
            e);
        log.error("Unexpected error in evaluation loop", e);
      }
    }

    log.debug("Detection evaluation thread stopped: {}", Thread.currentThread().getName());
  }

  private void evaluateEvent(MonitoringEvent event) {
    log.debug("Evaluating event: {}", event.getClass().getSimpleName());

    // Create detection context
    List<MonitoringEvent> recentEvents = new ArrayList<>(eventHistory);
    DetectionContext context = new DetectionContext(event, recentEvents, eventHistoryWindowMs);

    // Evaluate against all enabled rules
    for (DetectionRule rule : rules) {
      if (!rule.isEnabled()) {
        continue;
      }

      try {
        totalEvaluations.incrementAndGet();
        log.debug(
            TruthMarkers.TRUTH,
            "component=DetectionEngine event=rule_eval_started rule="
                + sanitize(rule.name())
                + " eventId="
                + sanitize(event.eventId()));
        log.info(
            TruthMarkers.TRUTH,
            "TRUTH stage=Detection action=rule_evaluation_started rule={} eventId={}",
            sanitize(rule.name()),
            sanitize(event.eventId()));

        long startEval = System.nanoTime();
        DetectionResult result = rule.evaluate(context);
        long evalDurationMs = (System.nanoTime() - startEval) / 1_000_000L;
        long endToEndLatencyMs =
            java.time.Duration.between(event.occurredAt(), java.time.Instant.now()).toMillis();

        log.debug(
            TruthMarkers.TRUTH,
            "component=DetectionEngine event=rule_eval_outcome rule="
                + sanitize(rule.name())
                + " detected="
                + result.isDetected()
                + " eventId="
                + sanitize(event.eventId()));

        if (result.isDetected()) {
          log.info("Rule {} detected suspicious activity", rule.name());
          log.info(
              TruthMarkers.TRUTH,
              "TRUTH stage=Detection action=rule_evaluated rule={} eventId={} result=matched description={} evalDurationMs={} endToEndLatencyMs={}",
              sanitize(rule.name()),
              sanitize(event.eventId()),
              sanitize(result.description()),
              evalDurationMs,
              endToEndLatencyMs);
          handleDetection(rule, result, event);
        } else {
          log.info(
              TruthMarkers.TRUTH,
              "TRUTH stage=Detection action=rule_evaluated rule={} eventId={} result=no_match evalDurationMs={} endToEndLatencyMs={}",
              sanitize(rule.name()),
              sanitize(event.eventId()),
              evalDurationMs,
              endToEndLatencyMs);
        }

      } catch (Exception e) {
        totalEvaluationFailures.incrementAndGet();
        log.info(
            TruthMarkers.TRUTH,
            "TRUTH stage=Detection action=rule_evaluated rule={} eventId={} result=failure err={}",
            sanitize(rule.name()),
            sanitize(event.eventId()),
            sanitize(e.getMessage()));
        log.error(
            TruthMarkers.TRUTH,
            "component=DetectionEngine event=rule_exception rule="
                + sanitize(rule.name())
                + " eventId="
                + sanitize(event.eventId())
                + " err="
                + sanitize(String.valueOf(e.getMessage())),
            e);
        log.error("Rule evaluation failed: {} - {}", rule.name(), e.getMessage(), e);
        // Continue with other rules (failure isolation)
      }
    }
  }

  private void handleDetection(DetectionRule rule, DetectionResult result, MonitoringEvent event) {
    // For mass-event rules (MassDeletion, RapidModification, SensitiveDirectory),
    // use rule-level cooldown. For file-specific rules, use per-file cooldown.
    String cooldownKey;
    if (rule instanceof com.filex.detection.rules.MassDeletionRule
        || rule instanceof com.filex.detection.rules.RapidModificationRule
        || rule instanceof com.filex.detection.rules.SensitiveDirectoryActivityRule) {
      // Rule-level cooldown for pattern-based detections
      cooldownKey = rule.name();
    } else {
      // Per-file cooldown for file-specific detections
      cooldownKey = rule.name() + ":" + event.path().toString();
    }

    Long lastDetection = lastDetectionTime.get(cooldownKey);
    long now = System.currentTimeMillis();

    if (lastDetection != null && (now - lastDetection) < DETECTION_COOLDOWN_MS) {
      totalSuppressedDetections.incrementAndGet();
      log.debug(
          TruthMarkers.TRUTH,
          "component=DetectionEngine event=detection_suppressed rule="
              + sanitize(rule.name())
              + " reason=cooldown lastFiredMsAgo="
              + (now - lastDetection));
      log.info(
          TruthMarkers.TRUTH,
          "TRUTH stage=Detection action=detection_cooldown rule={} eventId={} path={} result=suppressed",
          sanitize(rule.name()),
          sanitize(event.eventId()),
          (event.path() != null) ? sanitize(event.path().toString()) : "n/a");
      log.trace("Detection suppressed (cooldown): {}", rule.name());
      return;
    }

    lastDetectionTime.put(cooldownKey, now);

    // Publish detection event (increment counter only on successful publish)
    boolean published = publishDetectionEvent(rule, result, event);
    if (published) {
      totalDetections.incrementAndGet();
    }
  }

  private boolean publishDetectionEvent(
      DetectionRule rule, DetectionResult result, MonitoringEvent event) {
    try {
      DetectionEvent detectionEvent = createDetectionEvent(rule, result, event);
      log.info(
          TruthMarkers.TRUTH,
          "component=DetectionEngine event=detection_emitted rule="
              + sanitize(rule.name())
              + " severity="
              + sanitize(String.valueOf(result.severity()))
              + " confidence="
              + result.confidence()
              + " detectionId="
              + sanitize(detectionEvent.eventId())
              + " eventId="
              + sanitize(event.eventId()));

      // Use async publish to avoid blocking evaluation threads
      boolean queued = eventBus.tryPublishAsync(detectionEvent);

      if (queued) {
        log.info("Detection published: {} - {}", rule.name(), result.description());
        log.info(
            TruthMarkers.TRUTH,
            "TRUTH stage=Detection action=detection_published rule={} severity={} confidence={} detectionId={} eventId={} result=success",
            sanitize(rule.name()),
            sanitize(result.severity().name()),
            sanitize(result.confidence().name()),
            sanitize(detectionEvent.eventId()),
            sanitize(event.eventId()));
        return true;
      } else {
        totalFailedPublishes.incrementAndGet();
        log.error(
            "Failed to publish detection event (EventBus queue full): {} - {}",
            rule.name(),
            result.description());
        log.info(
            TruthMarkers.TRUTH,
            "TRUTH stage=Detection action=detection_published rule={} result=failure reason=queue_full",
            sanitize(rule.name()));
        return false;
      }

    } catch (Exception e) {
      totalFailedPublishes.incrementAndGet();
      log.error(
          "Failed to create/publish detection event: {} - {}", rule.name(), e.getMessage(), e);
      return false;
    }
  }

  private DetectionEvent createDetectionEvent(
      DetectionRule rule, DetectionResult result, MonitoringEvent event) {
    List<Path> affectedPaths = List.of(event.path());

    // Create appropriate detection event based on rule type
    if (rule instanceof com.filex.detection.rules.MassDeletionRule) {
      int deletionCount = (int) result.context().getOrDefault("deletionCount", 0);
      long windowMs = (long) result.context().getOrDefault("windowMs", 0L);
      return new MassDeletionDetectedEvent(
          rule.name(),
          result.severity(),
          result.confidence(),
          result.description(),
          affectedPaths,
          deletionCount,
          windowMs);
    } else if (rule instanceof com.filex.detection.rules.RapidModificationRule) {
      int modificationCount = (int) result.context().getOrDefault("modificationCount", 0);
      long windowMs = (long) result.context().getOrDefault("windowMs", 0L);
      return new RapidModificationDetectedEvent(
          rule.name(),
          result.severity(),
          result.confidence(),
          result.description(),
          affectedPaths,
          modificationCount,
          windowMs);
    } else if (rule instanceof com.filex.detection.rules.SuspiciousExtensionRenameRule) {
      String suspiciousExtension =
          (String) result.context().getOrDefault("suspiciousExtension", "unknown");
      return new SuspiciousRenameDetectedEvent(
          rule.name(),
          result.severity(),
          result.confidence(),
          result.description(),
          affectedPaths,
          suspiciousExtension);
    } else if (rule instanceof com.filex.detection.rules.HiddenFileCreationRule) {
      return new HiddenFileDetectedEvent(
          rule.name(), result.severity(), result.confidence(), result.description(), affectedPaths);
    } else if (rule instanceof com.filex.detection.rules.SensitiveDirectoryActivityRule) {
      String sensitiveDirectory =
          (String) result.context().getOrDefault("sensitiveDirectory", "unknown");
      return new SensitiveDirectoryAccessDetectedEvent(
          rule.name(),
          result.severity(),
          result.confidence(),
          result.description(),
          affectedPaths,
          sensitiveDirectory);
    }

    // Fallback: generic detection event
    return new HiddenFileDetectedEvent(
        rule.name(), result.severity(), result.confidence(), result.description(), affectedPaths);
  }
}
