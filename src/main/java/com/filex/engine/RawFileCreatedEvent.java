package com.filex.engine;

import java.nio.file.Path;

/**
 * Raw filesystem event indicating a file was created.
 *
 * <p>This is a raw, unnormalized event from the WatchService. Deduplication and normalization occur
 * downstream.
 */
public final class RawFileCreatedEvent extends MonitoringEvent {

  public RawFileCreatedEvent(Path path) {
    super(path);
  }

  public RawFileCreatedEvent(Path path, String correlationId) {
    super(path, correlationId);
  }
}
