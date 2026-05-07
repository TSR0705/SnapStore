package com.filex.detection.rules;

import com.filex.detection.*;
import com.filex.engine.RawFileCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Detects hidden file creation.
 *
 * <p>Triggers when hidden files are created (files starting with '.' on Unix
 * or with hidden attribute on Windows), which may indicate malware attempting
 * to conceal its presence.
 *
 * <p>Note: This rule has LOW confidence as hidden files are common in
 * legitimate scenarios (e.g., .gitignore, .config files).
 */
public final class HiddenFileCreationRule implements DetectionRule {

    private static final Logger log = LoggerFactory.getLogger(HiddenFileCreationRule.class);

    private volatile boolean enabled = true;

    @Override
    public String name() {
        return "HiddenFileCreationRule";
    }

    @Override
    public String description() {
        return "Detects hidden file creation that may indicate malware concealment";
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
        // Only evaluate on CREATE events
        if (!(context.currentEvent() instanceof RawFileCreatedEvent)) {
            return DetectionResult.notDetected();
        }

        RawFileCreatedEvent event = (RawFileCreatedEvent) context.currentEvent();
        Path path = event.path();
        String filename = path.getFileName().toString();

        // Check if file is hidden (starts with '.')
        // Note: On Windows, would need to check file attributes, but this is a simple heuristic
        if (filename.startsWith(".") && !filename.equals(".") && !filename.equals("..")) {
            // Filter out common legitimate hidden files
            if (isLegitimateHiddenFile(filename)) {
                return DetectionResult.notDetected();
            }

            log.debug("Hidden file creation detected: {}", path);

            Map<String, Object> contextData = new HashMap<>();
            contextData.put("filename", filename);

            return DetectionResult.detected(
                    Severity.LOW,
                    Confidence.LOW,
                    String.format("Hidden file created: %s", filename),
                    contextData
            );
        }

        return DetectionResult.notDetected();
    }

    private boolean isLegitimateHiddenFile(String filename) {
        // Common legitimate hidden files
        return filename.equals(".gitignore") ||
               filename.equals(".gitattributes") ||
               filename.equals(".editorconfig") ||
               filename.equals(".env") ||
               filename.equals(".dockerignore") ||
               filename.startsWith(".git") ||
               filename.startsWith(".idea") ||
               filename.startsWith(".vscode");
    }
}
