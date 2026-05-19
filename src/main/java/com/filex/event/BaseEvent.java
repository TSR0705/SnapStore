package com.filex.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Abstract base class for all events.
 *
 * <p>Provides common event infrastructure including ID generation, timestamp tracking, and source
 * identification.
 *
 * <p>All concrete events should extend this class to ensure consistent event metadata.
 */
public abstract class BaseEvent implements Event {

  private final String eventId;
  private final Instant timestamp;
  private final String source;
  private final String correlationId;

  protected BaseEvent(String source) {
    this(source, null);
  }

  protected BaseEvent(String source, String correlationId) {
    this.eventId = UUID.randomUUID().toString();
    this.timestamp = Instant.now();
    this.source = Objects.requireNonNull(source, "source must not be null");
    this.correlationId = correlationId;
  }

  @Override
  public final String getEventId() {
    return eventId;
  }

  @Override
  public final Instant getTimestamp() {
    return timestamp;
  }

  @Override
  public final String getSource() {
    return source;
  }

  @Override
  public final String getCorrelationId() {
    return correlationId;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName()
        + "{"
        + "eventId='"
        + eventId
        + '\''
        + ", timestamp="
        + timestamp
        + ", source='"
        + source
        + '\''
        + (correlationId != null ? ", correlationId='" + correlationId + '\'' : "")
        + '}';
  }
}
