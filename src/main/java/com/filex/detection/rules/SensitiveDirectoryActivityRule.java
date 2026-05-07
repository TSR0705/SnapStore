package com.filex.detection.rules;

import com.filex.detection.*;
import com.filex.engine.MonitoringEvent;
import com.filex.engine.RawFileDeletedEvent;
import com.filex.engine.RawFileModifiedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Detects suspicious activity in sensitive directories.
 *
 * <p>Triggers when unusual file operations occur in sensitive
 * system or user directories that are common ransomware targets.
 *
 * <p>Sensitive directories include:
 * <ul>
 *   <li>Documents</li>
 *   <li>Desktop</li>
 *   <li>Pictures</li>
 *   <li>Downloads</li>
 * </ul>
 */
public final class SensitiveDirectoryActivityRule implements DetectionRule {

    private static final Logger log = LoggerFactory.getLogger(SensitiveDirectoryActivityRule.class);

    private static final Set<String> SENSITIVE_DIRECTORIES = Set.of(
            "Documents", "Desktop", "Pictures", "Downloads",
            "Music", "Videos", "OneDrive", "Dropbox"
    );

    private static final long EVALUATION_WINDOW_MS = 5_000; // 5 seconds
    private static final int ACTIVITY_THRESHOLD = 10;

    private volatile boolean enabled = true;

    @Override
    public String name() {
        return "SensitiveDirectoryActivityRule";
    }

    @Override
    public String description() {
        return "Detects suspicious activity in sensitive user directories";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public DetectionResult evaluate(DetectionContext context) {
        MonitoringEvent event = context.currentEvent();
        
        // Only evaluate on DELETE or MODIFY events
        if (!(event instanceof RawFileDeletedEvent || event instanceof RawFileModifiedEvent)) {
            return DetectionResult.notDetected();
        }

        Path path = ((MonitoringEvent) event).path();

        // Check if path is in a sensitive directory
        String sensitiveDir = findSensitiveDirectory(path);
        if (sensitiveDir == null) {
            return DetectionResult.notDetected();
        }

        // Count recent activity in sensitive directories
        long recentSensitiveActivity = context.getEventsWithinWindow(EVALUATION_WINDOW_MS)
                .stream()
                .filter(e -> e instanceof RawFileDeletedEvent || e instanceof RawFileModifiedEvent)
                .filter(e -> findSensitiveDirectory(((MonitoringEvent) e).path()) != null)
                .count();

        if (recentSensitiveActivity >= ACTIVITY_THRESHOLD) {
            log.warn("Elevated activity in sensitive directory: {} ({} operations in {}ms)",
                    sensitiveDir, recentSensitiveActivity, EVALUATION_WINDOW_MS);

            Map<String, Object> contextData = new HashMap<>();
            contextData.put("sensitiveDirectory", sensitiveDir);
            contextData.put("activityCount", recentSensitiveActivity);
            contextData.put("windowMs", EVALUATION_WINDOW_MS);

            return DetectionResult.detected(
                    Severity.MEDIUM,
                    Confidence.MEDIUM,
                    String.format("Elevated activity in sensitive directory '%s': %d operations in %d seconds",
                            sensitiveDir, recentSensitiveActivity, EVALUATION_WINDOW_MS / 1000),
                    contextData
            );
        }

        return DetectionResult.notDetected();
    }

    private String findSensitiveDirectory(Path path) {
        String pathStr = path.toString();
        for (String sensitiveDir : SENSITIVE_DIRECTORIES) {
            if (pathStr.contains(sensitiveDir)) {
                return sensitiveDir;
            }
        }
        return null;
    }
}
