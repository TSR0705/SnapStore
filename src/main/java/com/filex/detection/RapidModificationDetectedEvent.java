package com.filex.detection;

import java.nio.file.Path;
import java.util.List;

/**
 * Detection event indicating rapid file modification activity.
 *
 * <p>Triggered when files are modified at an unusually high rate,
 * which may indicate encryption activity or automated tampering.
 */
public final class RapidModificationDetectedEvent extends DetectionEvent {

    private final int modificationCount;
    private final long windowMs;

    public RapidModificationDetectedEvent(
            String ruleName,
            Severity severity,
            Confidence confidence,
            String description,
            List<Path> affectedPaths,
            int modificationCount,
            long windowMs) {
        super(ruleName, severity, confidence, description, affectedPaths);
        this.modificationCount = modificationCount;
        this.windowMs = windowMs;
    }

    public int modificationCount() {
        return modificationCount;
    }

    public long windowMs() {
        return windowMs;
    }

    @Override
    public String toString() {
        return "RapidModificationDetectedEvent{" +
                "modificationCount=" + modificationCount +
                ", windowMs=" + windowMs +
                ", severity=" + severity() +
                ", confidence=" + confidence() +
                ", affectedPaths=" + affectedPaths().size() +
                '}';
    }
}
