package com.filex.engine;

import java.nio.file.Path;

/**
 * Raw filesystem event indicating a directory was created.
 *
 * <p>This event triggers dynamic watch registration for the new directory to maintain recursive
 * monitoring coverage.
 */
public final class RawDirectoryCreatedEvent extends MonitoringEvent {

  public RawDirectoryCreatedEvent(Path path) {
    super(path);
  }

  public RawDirectoryCreatedEvent(Path path, String correlationId) {
    super(path, correlationId);
  }
}
