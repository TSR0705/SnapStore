package com.filex.engine;

import com.filex.event.AppEvent;
import com.filex.event.EventPriority;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Base class for all filesystem monitoring events.
 *
 * <p>Monitoring events represent raw filesystem changes detected by the WatchService. These are
 * normalized and deduplicated before being persisted or processed by detection logic.
 */
public abstract class MonitoringEvent extends AppEvent {

  private final Path path;

  protected MonitoringEvent(Path path) {
    this(path, null, EventPriority.NORMAL);
  }

  protected MonitoringEvent(Path path, String correlationId) {
    this(path, correlationId, EventPriority.NORMAL);
  }

  protected MonitoringEvent(Path path, String correlationId, EventPriority priority) {
    super("MonitoringEngine", correlationId, priority);
    this.path = Objects.requireNonNull(path, "path must not be null");
  }

  /** The filesystem path associated with this monitoring event. */
  public Path path() {
    return path;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName()
        + "{"
        + "path="
        + path
        + ", eventId='"
        + eventId()
        + '\''
        + ", occurredAt="
        + occurredAt()
        + '}';
  }
}
