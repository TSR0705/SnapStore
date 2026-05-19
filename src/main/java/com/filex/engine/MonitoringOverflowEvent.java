package com.filex.engine;

import com.filex.event.AppEvent;
import com.filex.event.EventPriority;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Event indicating a WatchService OVERFLOW occurred.
 *
 * <p>OVERFLOW means events were lost because the OS event buffer filled faster than the watch loop
 * could process them.
 *
 * <p>This is a HIGH priority event requiring immediate attention and potential recovery actions.
 */
public final class MonitoringOverflowEvent extends AppEvent {

  private final Path affectedPath;

  public MonitoringOverflowEvent(Path affectedPath) {
    super("MonitoringEngine", null, EventPriority.HIGH);
    this.affectedPath = Objects.requireNonNull(affectedPath, "affectedPath must not be null");
  }

  public Path affectedPath() {
    return affectedPath;
  }

  @Override
  public String toString() {
    return "MonitoringOverflowEvent{"
        + "affectedPath="
        + affectedPath
        + ", eventId='"
        + eventId()
        + '\''
        + '}';
  }
}
