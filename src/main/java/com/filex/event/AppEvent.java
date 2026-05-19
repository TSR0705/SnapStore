package com.filex.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Base class for all application domain events.
 *
 * <p>Events are immutable value objects that represent something that has happened in the system.
 * They flow through the EventBus to decouple components.
 *
 * <p>All events provide:
 *
 * <ul>
 *   <li>Unique event ID for traceability
 *   <li>Timestamp of when the event occurred
 *   <li>Source identifier (component that created the event)
 *   <li>Optional correlation ID for event chains
 *   <li>Priority level for future priority-based dispatch
 * </ul>
 */
public abstract class AppEvent {

  private final String eventId;
  private final Instant occurredAt;
  private final String source;
  private final String correlationId;
  private final EventPriority priority;

  /**
   * @param source logical name of the component that produced this event
   */
  protected AppEvent(String source) {
    this(source, null, EventPriority.NORMAL);
  }

  /**
   * @param source logical name of the component that produced this event
   * @param correlationId optional correlation ID for event chains
   */
  protected AppEvent(String source, String correlationId) {
    this(source, correlationId, EventPriority.NORMAL);
  }

  /**
   * @param source logical name of the component that produced this event
   * @param correlationId optional correlation ID for event chains
   * @param priority event priority level for future priority-based dispatch
   */
  protected AppEvent(String source, String correlationId, EventPriority priority) {
    this.eventId = UUID.randomUUID().toString();
    this.source = Objects.requireNonNull(source, "source must not be null");
    this.occurredAt = Instant.now();
    this.correlationId = correlationId;
    this.priority = Objects.requireNonNull(priority, "priority must not be null");
  }

  /** Unique identifier for this event. */
  public String eventId() {
    return eventId;
  }

  /** Timestamp at which this event was created. */
  public Instant occurredAt() {
    return occurredAt;
  }

  /** Logical name of the originating component. */
  public String source() {
    return source;
  }

  /** Optional correlation ID for event chains. */
  public String correlationId() {
    return correlationId;
  }

  /** Event priority level for future priority-based dispatch. */
  public EventPriority priority() {
    return priority;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName()
        + "{"
        + "eventId='"
        + eventId
        + '\''
        + ", source='"
        + source
        + '\''
        + ", occurredAt="
        + occurredAt
        + ", priority="
        + priority
        + (correlationId != null ? ", correlationId='" + correlationId + '\'' : "")
        + '}';
  }
}
