package com.filex.engine;

import java.nio.file.Path;

/**
 * Raw filesystem event indicating a file was deleted.
 *
 * <p>This is a raw, unnormalized event from the WatchService.
 * Deduplication and normalization occur downstream.
 */
public final class RawFileDeletedEvent extends MonitoringEvent {

    public RawFileDeletedEvent(Path path) {
        super(path);
    }

    public RawFileDeletedEvent(Path path, String correlationId) {
        super(path, correlationId);
    }
}
