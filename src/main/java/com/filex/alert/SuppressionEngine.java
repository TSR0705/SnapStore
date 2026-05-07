package com.filex.alert;

import com.filex.detection.DetectionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Suppression engine for preventing alert spam.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Track recent alerts by suppression key</li>
 *   <li>Enforce cooldown windows</li>
 *   <li>Prevent duplicate alert flooding</li>
 *   <li>Manage bounded suppression memory</li>
 * </ul>
 *
 * <p>Suppression strategy:
 * <ul>
 *   <li>Same suppression key within cooldown → suppress</li>
 *   <li>Different suppression key → allow</li>
 *   <li>Expired cooldown → allow</li>
 * </ul>
 *
 * <p>Thread-safety: All methods are thread-safe.
 */
public final class SuppressionEngine {

    private static final Logger log = LoggerFactory.getLogger(SuppressionEngine.class);

    private static final long DEFAULT_COOLDOWN_MS = 60_000; // 1 minute
    private static final int MAX_SUPPRESSION_ENTRIES = 10000;

    private final long cooldownMs;
    private final Map<String, Long> suppressionCache = new ConcurrentHashMap<>();

    public SuppressionEngine() {
        this(DEFAULT_COOLDOWN_MS);
    }

    public SuppressionEngine(long cooldownMs) {
        this.cooldownMs = cooldownMs;
        log.info("SuppressionEngine initialized with cooldown: {}ms", cooldownMs);
    }

    /**
     * Checks if an alert should be suppressed.
     *
     * @param detection the detection event to check
     * @return true if alert should be suppressed, false if it should be processed
     */
    public boolean shouldSuppress(DetectionEvent detection) {
        String suppressionKey = generateSuppressionKey(detection);
        
        Long lastAlertTime = suppressionCache.get(suppressionKey);
        long now = System.currentTimeMillis();

        if (lastAlertTime != null) {
            long timeSinceLastAlert = now - lastAlertTime;
            if (timeSinceLastAlert < cooldownMs) {
                log.debug("Suppressing alert for key: {} ({}ms since last alert)",
                        suppressionKey, timeSinceLastAlert);
                return true;
            }
        }

        // Not suppressed - record this alert
        suppressionCache.put(suppressionKey, now);
        
        // Enforce bounded memory
        if (suppressionCache.size() > MAX_SUPPRESSION_ENTRIES) {
            cleanupExpiredSuppressions();
        }

        return false;
    }

    /**
     * Removes expired suppression entries to prevent unbounded memory growth.
     *
     * @return number of entries removed
     */
    public int cleanupExpiredSuppressions() {
        long now = System.currentTimeMillis();
        long cutoffTime = now - cooldownMs;
        
        int removed = 0;
        Iterator<Map.Entry<String, Long>> iterator = 
                suppressionCache.entrySet().iterator();
        
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (entry.getValue() < cutoffTime) {
                iterator.remove();
                removed++;
            }
        }

        if (removed > 0) {
            log.info("Cleaned up {} expired suppression entries", removed);
        }

        return removed;
    }

    /**
     * Returns the number of active suppression entries.
     */
    public int getSuppressionCacheSize() {
        return suppressionCache.size();
    }

    /**
     * Clears all suppression state. Used for testing and shutdown.
     */
    public void clear() {
        suppressionCache.clear();
        log.debug("Suppression state cleared");
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Generates a suppression key from a detection event.
     *
     * <p>Suppression is more granular than correlation:
     * <ul>
     *   <li>Same rule + same severity + same path → suppress</li>
     *   <li>Different severity → allow (escalation)</li>
     *   <li>Different path → allow (different target)</li>
     * </ul>
     */
    private String generateSuppressionKey(DetectionEvent detection) {
        StringBuilder key = new StringBuilder();
        key.append(detection.ruleName());
        key.append(":");
        key.append(detection.severity());
        
        // Include primary affected path for file-specific suppression
        if (!detection.affectedPaths().isEmpty()) {
            key.append(":");
            key.append(detection.affectedPaths().get(0).toString());
        }

        return key.toString();
    }
}
