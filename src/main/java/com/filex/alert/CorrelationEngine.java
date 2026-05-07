package com.filex.alert;

import com.filex.detection.DetectionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Correlation engine for grouping related detections into incidents.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Generate correlation keys from detection events</li>
 *   <li>Track active incidents by correlation key</li>
 *   <li>Determine merge vs create decisions</li>
 *   <li>Manage correlation memory with bounded growth</li>
 * </ul>
 *
 * <p>Correlation strategy:
 * <ul>
 *   <li>Same correlation key + within time window → merge</li>
 *   <li>Different correlation key → new incident</li>
 *   <li>Expired correlation → new incident</li>
 * </ul>
 *
 * <p>Thread-safety: All methods are thread-safe.
 */
public final class CorrelationEngine {

    private static final Logger log = LoggerFactory.getLogger(CorrelationEngine.class);

    private static final long DEFAULT_CORRELATION_WINDOW_MS = 300_000; // 5 minutes
    private static final int MAX_ACTIVE_CORRELATIONS = 10000;

    private final long correlationWindowMs;
    private final Map<String, CorrelationEntry> activeCorrelations = new ConcurrentHashMap<>();

    public CorrelationEngine() {
        this(DEFAULT_CORRELATION_WINDOW_MS);
    }

    public CorrelationEngine(long correlationWindowMs) {
        this.correlationWindowMs = correlationWindowMs;
        log.info("CorrelationEngine initialized with window: {}ms", correlationWindowMs);
    }

    /**
     * Finds an existing incident that should be merged with this detection,
     * or returns null if a new incident should be created.
     *
     * @param detection the detection event to correlate
     * @return existing incident ID to merge with, or null for new incident
     */
    public String findCorrelatedIncident(DetectionEvent detection) {
        String correlationKey = generateCorrelationKey(detection);
        
        CorrelationEntry entry = activeCorrelations.get(correlationKey);
        if (entry == null) {
            return null;
        }

        // Check if correlation is still within time window
        long now = System.currentTimeMillis();
        if (now - entry.lastSeenMs > correlationWindowMs) {
            log.debug("Correlation expired for key: {}", correlationKey);
            activeCorrelations.remove(correlationKey);
            return null;
        }

        log.debug("Found correlated incident: {} for key: {}", entry.incidentId, correlationKey);
        return entry.incidentId;
    }

    /**
     * Registers a new incident for correlation tracking.
     *
     * @param detection the detection that created the incident
     * @param incidentId the incident ID to track
     */
    public void registerIncident(DetectionEvent detection, String incidentId) {
        String correlationKey = generateCorrelationKey(detection);
        
        CorrelationEntry entry = new CorrelationEntry(
                incidentId,
                correlationKey,
                System.currentTimeMillis()
        );
        
        activeCorrelations.put(correlationKey, entry);
        log.debug("Registered incident {} with correlation key: {}", incidentId, correlationKey);

        // Enforce bounded memory
        if (activeCorrelations.size() > MAX_ACTIVE_CORRELATIONS) {
            cleanupExpiredCorrelations();
        }
    }

    /**
     * Updates the last seen time for an existing correlation.
     *
     * @param detection the detection event
     */
    public void updateCorrelation(DetectionEvent detection) {
        String correlationKey = generateCorrelationKey(detection);
        
        CorrelationEntry entry = activeCorrelations.get(correlationKey);
        if (entry != null) {
            entry.lastSeenMs = System.currentTimeMillis();
            log.trace("Updated correlation timestamp for key: {}", correlationKey);
        }
    }

    /**
     * Removes expired correlations to prevent unbounded memory growth.
     *
     * @return number of correlations removed
     */
    public int cleanupExpiredCorrelations() {
        long now = System.currentTimeMillis();
        long cutoffTime = now - correlationWindowMs;
        
        int removed = 0;
        Iterator<Map.Entry<String, CorrelationEntry>> iterator = 
                activeCorrelations.entrySet().iterator();
        
        while (iterator.hasNext()) {
            Map.Entry<String, CorrelationEntry> entry = iterator.next();
            if (entry.getValue().lastSeenMs < cutoffTime) {
                iterator.remove();
                removed++;
            }
        }

        if (removed > 0) {
            log.info("Cleaned up {} expired correlations", removed);
        }

        return removed;
    }

    /**
     * Returns the number of active correlations being tracked.
     */
    public int getActiveCorrelationCount() {
        return activeCorrelations.size();
    }

    /**
     * Clears all correlation state. Used for testing and shutdown.
     */
    public void clear() {
        activeCorrelations.clear();
        log.debug("Correlation state cleared");
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Generates a correlation key from a detection event.
     *
     * <p>Correlation strategy:
     * <ul>
     *   <li>MassDeletion + RapidModification + SuspiciousRename → ransomware pattern</li>
     *   <li>Same rule + same path → file-specific pattern</li>
     *   <li>Same rule + different paths → rule-specific pattern</li>
     * </ul>
     */
    private String generateCorrelationKey(DetectionEvent detection) {
        String ruleName = detection.ruleName();
        
        // For mass-event rules, correlate by rule type only
        if (ruleName.contains("MassDeletion") || 
            ruleName.contains("RapidModification") ||
            ruleName.contains("SensitiveDirectory")) {
            return "mass-event:" + ruleName;
        }

        // For file-specific rules, correlate by rule + path
        if (!detection.affectedPaths().isEmpty()) {
            String primaryPath = detection.affectedPaths().get(0).toString();
            return "file-specific:" + ruleName + ":" + primaryPath;
        }

        // Fallback: correlate by rule only
        return "rule:" + ruleName;
    }

    /**
     * Internal correlation tracking entry.
     */
    private static final class CorrelationEntry {
        final String incidentId;
        final String correlationKey;
        volatile long lastSeenMs;

        CorrelationEntry(String incidentId, String correlationKey, long lastSeenMs) {
            this.incidentId = incidentId;
            this.correlationKey = correlationKey;
            this.lastSeenMs = lastSeenMs;
        }
    }
}
