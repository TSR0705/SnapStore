package com.filex.engine;

import java.nio.file.Path;

/**
 * Raw filesystem event indicating a file was modified.
 *
 * <p>This is a raw, unnormalized event from the WatchService.
 * MODIFY events are particularly noisy and require aggressive
 * deduplication to prevent event storms.
 */
public final class RawFileModifiedEvent extends MonitoringEvent {

    public RawFileModifiedEvent(Path path) {
        super(path);
    }

    public RawFileModifiedEvent(Path path, String correlationId) {
        super(path, correlationId);
    }
}
