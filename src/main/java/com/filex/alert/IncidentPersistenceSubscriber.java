package com.filex.alert;

import com.filex.detection.DetectionEvent;
import com.filex.event.EventBus;
import com.filex.persistence.IncidentPersistenceService;
import com.filex.persistence.PersistenceException;
import com.filex.validation.TruthMarkers;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Subscribes to incident events and persists them to the database.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Listen to IncidentCreatedEvent and IncidentUpdatedEvent
 *   <li>Persist incidents, evidence, and timeline records
 *   <li>Track detection events for evidence linking
 *   <li>Isolate persistence failures from runtime
 * </ul>
 *
 * <p>Design principles:
 *
 * <ul>
 *   <li>Persistence failures do NOT crash the alert engine
 *   <li>Failed persistence is logged and counted in metrics
 *   <li>Async persistence (does not block incident creation)
 *   <li>No direct coupling between AlertEngine and persistence layer
 * </ul>
 *
 * <p>Thread-safety: This subscriber is thread-safe and can handle concurrent incident events.
 */
public final class IncidentPersistenceSubscriber {

  private static final Logger log = LoggerFactory.getLogger(IncidentPersistenceSubscriber.class);
  private static final int MAX_RECENT_DETECTIONS = 10000;

  private final EventBus eventBus;
  private final IncidentPersistenceService persistenceService;
  private volatile boolean started = false;

  // Track recent detection events for evidence linking
  // Maps detection ID -> DetectionEvent
  // Using LinkedHashMap with access-order for proper LRU eviction
  private final Map<String, DetectionEvent> recentDetections =
      Collections.synchronizedMap(
          new LinkedHashMap<String, DetectionEvent>(MAX_RECENT_DETECTIONS + 1, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, DetectionEvent> eldest) {
              return size() > MAX_RECENT_DETECTIONS;
            }
          });

  public IncidentPersistenceSubscriber(
      EventBus eventBus, IncidentPersistenceService persistenceService) {
    this.eventBus = Objects.requireNonNull(eventBus, "eventBus must not be null");
    this.persistenceService =
        Objects.requireNonNull(persistenceService, "persistenceService must not be null");
  }

  /** Starts subscribing to incident and detection events. */
  public void start() {
    if (started) return;
    log.info("Starting incident persistence subscriber...");
    started = true;

    // Subscribe to incident events
    eventBus.subscribe(IncidentCreatedEvent.class, this::onIncidentCreated);
    eventBus.subscribe(IncidentUpdatedEvent.class, this::onIncidentUpdated);

    // Subscribe to detection events to track them for evidence linking
    eventBus.subscribe(com.filex.detection.MassDeletionDetectedEvent.class, this::onDetectionEvent);
    eventBus.subscribe(
        com.filex.detection.RapidModificationDetectedEvent.class, this::onDetectionEvent);
    eventBus.subscribe(
        com.filex.detection.SuspiciousRenameDetectedEvent.class, this::onDetectionEvent);
    eventBus.subscribe(com.filex.detection.HiddenFileDetectedEvent.class, this::onDetectionEvent);
    eventBus.subscribe(
        com.filex.detection.SensitiveDirectoryAccessDetectedEvent.class, this::onDetectionEvent);

    log.info(
        TruthMarkers.TRUTH, "component=IncidentPersistenceSubscriber event=subscriber_started");
    // TODO: Subscriber currently has no AppConfig/DatabaseManager reference; add one without
    // changing constructor signature in a future task to surface the real database file path.
    log.info(
        TruthMarkers.TRUTH, "component=IncidentPersistenceSubscriber event=db_path path=unknown");
    // TODO: Surface schema version from DatabaseManager once injected; persistenceService
    // does not currently expose a schema version accessor.
    log.info(
        TruthMarkers.TRUTH,
        "component=IncidentPersistenceSubscriber event=schema_version v=unknown");

    log.info("Incident persistence subscriber started");
  }

  /** Stops subscribing to events. */
  public void stop() {
    if (!started) return;
    log.info("Stopping incident persistence subscriber...");
    started = false;

    // Unsubscribe from incident events
    eventBus.unsubscribe(IncidentCreatedEvent.class, this::onIncidentCreated);
    eventBus.unsubscribe(IncidentUpdatedEvent.class, this::onIncidentUpdated);

    // Unsubscribe from detection events
    eventBus.unsubscribe(
        com.filex.detection.MassDeletionDetectedEvent.class, this::onDetectionEvent);
    eventBus.unsubscribe(
        com.filex.detection.RapidModificationDetectedEvent.class, this::onDetectionEvent);
    eventBus.unsubscribe(
        com.filex.detection.SuspiciousRenameDetectedEvent.class, this::onDetectionEvent);
    eventBus.unsubscribe(com.filex.detection.HiddenFileDetectedEvent.class, this::onDetectionEvent);
    eventBus.unsubscribe(
        com.filex.detection.SensitiveDirectoryAccessDetectedEvent.class, this::onDetectionEvent);

    // Clear tracking
    recentDetections.clear();

    log.info(
        TruthMarkers.TRUTH, "component=IncidentPersistenceSubscriber event=subscriber_stopped");
    log.info("Incident persistence subscriber stopped");
  }

  /**
   * Clears in-memory tracking state for truth-validation runs. Intended to be called only by {@code
   * TruthValidationCoordinator} while the subscriber/engines are still IDLE. No state guard is
   * enforced here.
   */
  public void resetForValidation() {
    recentDetections.clear();
    log.info(
        TruthMarkers.TRUTH,
        "component=IncidentPersistenceSubscriber event=reset_for_validation_complete");
  }

  public boolean isStarted() {
    return started;
  }

  // -------------------------------------------------------------------------
  // Event handlers
  // -------------------------------------------------------------------------

  private void onDetectionEvent(DetectionEvent detection) {
    // Track detection for evidence linking
    // LinkedHashMap with access-order automatically evicts oldest entries
    recentDetections.put(detection.eventId(), detection);
  }

  private void onIncidentCreated(IncidentCreatedEvent event) {
    Incident incident = event.getIncident();
    log.info(
        TruthMarkers.TRUTH,
        "TRUTH stage=Persistence action=event_received incidentId={} eventType=IncidentCreatedEvent",
        incident.getIncidentId());

    try {
      // Find the detection that triggered this incident
      DetectionEvent detection = findDetectionForIncident(incident);

      if (detection != null) {
        log.debug(
            TruthMarkers.TRUTH,
            "component=IncidentPersistenceSubscriber event=persist_attempt incidentId={} kind=created",
            incident.getIncidentId());
        persistenceService.persistNewIncident(incident, detection);
        log.info(
            TruthMarkers.TRUTH,
            "TRUTH stage=Persistence action=persist_incident incidentId={} type=create result=success",
            incident.getIncidentId());
        log.debug(
            TruthMarkers.TRUTH,
            "component=IncidentPersistenceSubscriber event=persist_result incidentId={} kind=created outcome=ok",
            incident.getIncidentId());
        log.debug("Persisted new incident: {}", incident.getIncidentId());
      } else {
        log.warn("Cannot persist incident {}: no detection found", incident.getIncidentId());
      }

    } catch (PersistenceException e) {
      // Log but don't crash - persistence failures are isolated
      log.info(
          TruthMarkers.TRUTH,
          "TRUTH stage=Persistence action=persist_incident incidentId={} type=create result=failure err={}",
          incident.getIncidentId(),
          e.getMessage());
      log.error(
          TruthMarkers.TRUTH,
          "component=IncidentPersistenceSubscriber event=persist_result incidentId={} kind=created outcome=fail err={}",
          incident.getIncidentId(),
          e.getMessage(),
          e);
      log.error("Failed to persist incident creation: {}", incident.getIncidentId(), e);
    }
  }

  private void onIncidentUpdated(IncidentUpdatedEvent event) {
    Incident incident = event.getIncident();
    String updateReason = event.getUpdateReason();
    log.info(
        TruthMarkers.TRUTH,
        "TRUTH stage=Persistence action=event_received incidentId={} eventType=IncidentUpdatedEvent reason={}",
        incident.getIncidentId(),
        updateReason != null ? updateReason : "none");

    try {
      // Try to find the detection that triggered this update
      DetectionEvent detection = findDetectionForIncident(incident);

      log.debug(
          TruthMarkers.TRUTH,
          "component=IncidentPersistenceSubscriber event=persist_attempt incidentId={} kind=updated",
          incident.getIncidentId());
      persistenceService.persistIncidentUpdate(incident, detection, updateReason);
      log.info(
          TruthMarkers.TRUTH,
          "TRUTH stage=Persistence action=persist_incident incidentId={} type=update result=success",
          incident.getIncidentId());
      log.debug(
          TruthMarkers.TRUTH,
          "component=IncidentPersistenceSubscriber event=persist_result incidentId={} kind=updated outcome=ok",
          incident.getIncidentId());
      log.debug("Persisted incident update: {}", incident.getIncidentId());

    } catch (PersistenceException e) {
      // Log but don't crash - persistence failures are isolated
      log.info(
          TruthMarkers.TRUTH,
          "TRUTH stage=Persistence action=persist_incident incidentId={} type=update result=failure err={}",
          incident.getIncidentId(),
          e.getMessage());
      log.error(
          TruthMarkers.TRUTH,
          "component=IncidentPersistenceSubscriber event=persist_result incidentId={} kind=updated outcome=fail err={}",
          incident.getIncidentId(),
          e.getMessage(),
          e);
      log.error("Failed to persist incident update: {}", incident.getIncidentId(), e);
    }
  }

  // -------------------------------------------------------------------------
  // Private helpers
  // -------------------------------------------------------------------------

  private DetectionEvent findDetectionForIncident(Incident incident) {
    // Try to find the most recent detection linked to this incident
    if (incident.getLinkedDetectionIds().isEmpty()) {
      return null;
    }

    // Get the last detection ID (most recent)
    String lastDetectionId =
        incident.getLinkedDetectionIds().get(incident.getLinkedDetectionIds().size() - 1);

    return recentDetections.get(lastDetectionId);
  }
}
