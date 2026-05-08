package com.filex.alert;

import com.filex.detection.DetectionEvent;
import com.filex.event.EventBus;
import com.filex.persistence.IncidentPersistenceService;
import com.filex.persistence.PersistenceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Subscribes to incident events and persists them to the database.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Listen to IncidentCreatedEvent and IncidentUpdatedEvent</li>
 *   <li>Persist incidents, evidence, and timeline records</li>
 *   <li>Track detection events for evidence linking</li>
 *   <li>Isolate persistence failures from runtime</li>
 * </ul>
 *
 * <p>Design principles:
 * <ul>
 *   <li>Persistence failures do NOT crash the alert engine</li>
 *   <li>Failed persistence is logged and counted in metrics</li>
 *   <li>Async persistence (does not block incident creation)</li>
 *   <li>No direct coupling between AlertEngine and persistence layer</li>
 * </ul>
 *
 * <p>Thread-safety: This subscriber is thread-safe and can handle
 * concurrent incident events.
 */
public final class IncidentPersistenceSubscriber {

    private static final Logger log = LoggerFactory.getLogger(IncidentPersistenceSubscriber.class);
    private static final int MAX_RECENT_DETECTIONS = 10000;

    private final EventBus eventBus;
    private final IncidentPersistenceService persistenceService;

    // Track recent detection events for evidence linking
    // Maps detection ID -> DetectionEvent
    // Using LinkedHashMap with access-order for proper LRU eviction
    private final Map<String, DetectionEvent> recentDetections = Collections.synchronizedMap(
            new LinkedHashMap<String, DetectionEvent>(MAX_RECENT_DETECTIONS + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, DetectionEvent> eldest) {
                    return size() > MAX_RECENT_DETECTIONS;
                }
            }
    );

    public IncidentPersistenceSubscriber(EventBus eventBus, IncidentPersistenceService persistenceService) {
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus must not be null");
        this.persistenceService = Objects.requireNonNull(persistenceService, "persistenceService must not be null");
    }

    /**
     * Starts subscribing to incident and detection events.
     */
    public void start() {
        log.info("Starting incident persistence subscriber...");

        // Subscribe to incident events
        eventBus.subscribe(IncidentCreatedEvent.class, this::onIncidentCreated);
        eventBus.subscribe(IncidentUpdatedEvent.class, this::onIncidentUpdated);

        // Subscribe to detection events to track them for evidence linking
        eventBus.subscribe(com.filex.detection.MassDeletionDetectedEvent.class, this::onDetectionEvent);
        eventBus.subscribe(com.filex.detection.RapidModificationDetectedEvent.class, this::onDetectionEvent);
        eventBus.subscribe(com.filex.detection.SuspiciousRenameDetectedEvent.class, this::onDetectionEvent);
        eventBus.subscribe(com.filex.detection.HiddenFileDetectedEvent.class, this::onDetectionEvent);
        eventBus.subscribe(com.filex.detection.SensitiveDirectoryAccessDetectedEvent.class, this::onDetectionEvent);

        log.info("Incident persistence subscriber started");
    }

    /**
     * Stops subscribing to events.
     */
    public void stop() {
        log.info("Stopping incident persistence subscriber...");

        // Unsubscribe from incident events
        eventBus.unsubscribe(IncidentCreatedEvent.class, this::onIncidentCreated);
        eventBus.unsubscribe(IncidentUpdatedEvent.class, this::onIncidentUpdated);

        // Unsubscribe from detection events
        eventBus.unsubscribe(com.filex.detection.MassDeletionDetectedEvent.class, this::onDetectionEvent);
        eventBus.unsubscribe(com.filex.detection.RapidModificationDetectedEvent.class, this::onDetectionEvent);
        eventBus.unsubscribe(com.filex.detection.SuspiciousRenameDetectedEvent.class, this::onDetectionEvent);
        eventBus.unsubscribe(com.filex.detection.HiddenFileDetectedEvent.class, this::onDetectionEvent);
        eventBus.unsubscribe(com.filex.detection.SensitiveDirectoryAccessDetectedEvent.class, this::onDetectionEvent);

        // Clear tracking
        recentDetections.clear();

        log.info("Incident persistence subscriber stopped");
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
        
        try {
            // Find the detection that triggered this incident
            DetectionEvent detection = findDetectionForIncident(incident);
            
            if (detection != null) {
                persistenceService.persistNewIncident(incident, detection);
                log.debug("Persisted new incident: {}", incident.getIncidentId());
            } else {
                log.warn("Cannot persist incident {}: no detection found", incident.getIncidentId());
            }
            
        } catch (PersistenceException e) {
            // Log but don't crash - persistence failures are isolated
            log.error("Failed to persist incident creation: {}", incident.getIncidentId(), e);
        }
    }

    private void onIncidentUpdated(IncidentUpdatedEvent event) {
        Incident incident = event.getIncident();
        String updateReason = event.getUpdateReason();
        
        try {
            // Try to find the detection that triggered this update
            DetectionEvent detection = findDetectionForIncident(incident);
            
            persistenceService.persistIncidentUpdate(incident, detection, updateReason);
            log.debug("Persisted incident update: {}", incident.getIncidentId());
            
        } catch (PersistenceException e) {
            // Log but don't crash - persistence failures are isolated
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
        String lastDetectionId = incident.getLinkedDetectionIds().get(
                incident.getLinkedDetectionIds().size() - 1
        );

        return recentDetections.get(lastDetectionId);
    }
}
