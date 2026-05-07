package com.filex.detection;

import java.nio.file.Path;
import java.util.List;

/**
 * Detection event indicating hidden file creation.
 *
 * <p>Triggered when hidden files are created, which may indicate
 * malware attempting to conceal its presence.
 */
public final class HiddenFileDetectedEvent extends DetectionEvent {

    public HiddenFileDetectedEvent(
            String ruleName,
            Severity severity,
            Confidence confidence,
            String description,
            List<Path> affectedPaths) {
        super(ruleName, severity, confidence, description, affectedPaths);
    }
}
