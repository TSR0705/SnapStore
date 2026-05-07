package com.filex.detection;

import java.nio.file.Path;
import java.util.List;

/**
 * Detection event indicating suspicious activity in sensitive directories.
 *
 * <p>Triggered when unusual file operations occur in sensitive
 * system or user directories (e.g., Documents, Desktop, System32).
 */
public final class SensitiveDirectoryAccessDetectedEvent extends DetectionEvent {

    private final String sensitiveDirectory;

    public SensitiveDirectoryAccessDetectedEvent(
            String ruleName,
            Severity severity,
            Confidence confidence,
            String description,
            List<Path> affectedPaths,
            String sensitiveDirectory) {
        super(ruleName, severity, confidence, description, affectedPaths);
        this.sensitiveDirectory = sensitiveDirectory;
    }

    public String sensitiveDirectory() {
        return sensitiveDirectory;
    }

    @Override
    public String toString() {
        return "SensitiveDirectoryAccessDetectedEvent{" +
                "sensitiveDirectory='" + sensitiveDirectory + '\'' +
                ", severity=" + severity() +
                ", confidence=" + confidence() +
                ", affectedPaths=" + affectedPaths().size() +
                '}';
    }
}
