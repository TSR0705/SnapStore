package com.filex.detection;

import java.nio.file.Path;
import java.util.List;

/**
 * Detection event indicating suspicious file extension changes.
 *
 * <p>Triggered when files are renamed with suspicious extensions
 * (e.g., .encrypted, .locked, .crypted) which may indicate
 * ransomware encryption activity.
 */
public final class SuspiciousRenameDetectedEvent extends DetectionEvent {

    private final String suspiciousExtension;

    public SuspiciousRenameDetectedEvent(
            String ruleName,
            Severity severity,
            Confidence confidence,
            String description,
            List<Path> affectedPaths,
            String suspiciousExtension) {
        super(ruleName, severity, confidence, description, affectedPaths);
        this.suspiciousExtension = suspiciousExtension;
    }

    public String suspiciousExtension() {
        return suspiciousExtension;
    }

    @Override
    public String toString() {
        return "SuspiciousRenameDetectedEvent{" +
                "suspiciousExtension='" + suspiciousExtension + '\'' +
                ", severity=" + severity() +
                ", confidence=" + confidence() +
                ", affectedPaths=" + affectedPaths().size() +
                '}';
    }
}
