package com.filex.detection;

import com.filex.event.AppEvent;
import com.filex.event.EventPriority;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Base class for all detection events.
 *
 * <p>Detection events represent suspicious activities identified by the detection engine. They are
 * published to the EventBus for downstream processing (persistence, alerting, etc.).
 */
public abstract class DetectionEvent extends AppEvent {

  private final String ruleName;
  private final Severity severity;
  private final Confidence confidence;
  private final String description;
  private final List<Path> affectedPaths;

  protected DetectionEvent(
      String ruleName,
      Severity severity,
      Confidence confidence,
      String description,
      List<Path> affectedPaths) {
    this(ruleName, severity, confidence, description, affectedPaths, null);
  }

  protected DetectionEvent(
      String ruleName,
      Severity severity,
      Confidence confidence,
      String description,
      List<Path> affectedPaths,
      String correlationId) {
    super("DetectionEngine", correlationId, mapSeverityToPriority(severity));
    this.ruleName = Objects.requireNonNull(ruleName, "ruleName must not be null");
    this.severity = Objects.requireNonNull(severity, "severity must not be null");
    this.confidence = Objects.requireNonNull(confidence, "confidence must not be null");
    this.description = Objects.requireNonNull(description, "description must not be null");
    this.affectedPaths =
        affectedPaths != null ? Collections.unmodifiableList(affectedPaths) : List.of();
  }

  private static EventPriority mapSeverityToPriority(Severity severity) {
    return switch (severity) {
      case CRITICAL, HIGH -> EventPriority.HIGH;
      case MEDIUM -> EventPriority.NORMAL;
      case LOW -> EventPriority.LOW;
    };
  }

  public String ruleName() {
    return ruleName;
  }

  public Severity severity() {
    return severity;
  }

  public Confidence confidence() {
    return confidence;
  }

  public String description() {
    return description;
  }

  public List<Path> affectedPaths() {
    return affectedPaths;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName()
        + "{"
        + "ruleName='"
        + ruleName
        + '\''
        + ", severity="
        + severity
        + ", confidence="
        + confidence
        + ", description='"
        + description
        + '\''
        + ", affectedPaths="
        + affectedPaths.size()
        + " path(s)"
        + ", eventId='"
        + eventId()
        + '\''
        + '}';
  }
}
