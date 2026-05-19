package com.filex.alert;

/**
 * Immutable snapshot of AlertEngine runtime metrics.
 *
 * <p>Provides observability into alert processing performance and behavior.
 */
public final class AlertMetrics {

  private final long totalAlertsProcessed;
  private final long totalIncidentsCreated;
  private final long totalIncidentsMerged;
  private final long totalAlertsSuppressed;
  private final long totalCorrelationFailures;
  private final long totalDroppedAlerts;
  private final int activeIncidentCount;
  private final int processingQueueDepth;
  private final AlertState currentState;

  public AlertMetrics(
      long totalAlertsProcessed,
      long totalIncidentsCreated,
      long totalIncidentsMerged,
      long totalAlertsSuppressed,
      long totalCorrelationFailures,
      long totalDroppedAlerts,
      int activeIncidentCount,
      int processingQueueDepth,
      AlertState currentState) {
    this.totalAlertsProcessed = totalAlertsProcessed;
    this.totalIncidentsCreated = totalIncidentsCreated;
    this.totalIncidentsMerged = totalIncidentsMerged;
    this.totalAlertsSuppressed = totalAlertsSuppressed;
    this.totalCorrelationFailures = totalCorrelationFailures;
    this.totalDroppedAlerts = totalDroppedAlerts;
    this.activeIncidentCount = activeIncidentCount;
    this.processingQueueDepth = processingQueueDepth;
    this.currentState = currentState;
  }

  public long getTotalAlertsProcessed() {
    return totalAlertsProcessed;
  }

  public long getTotalIncidentsCreated() {
    return totalIncidentsCreated;
  }

  public long getTotalIncidentsMerged() {
    return totalIncidentsMerged;
  }

  public long getTotalAlertsSuppressed() {
    return totalAlertsSuppressed;
  }

  public long getTotalCorrelationFailures() {
    return totalCorrelationFailures;
  }

  public long getTotalDroppedAlerts() {
    return totalDroppedAlerts;
  }

  public int getActiveIncidentCount() {
    return activeIncidentCount;
  }

  public int getProcessingQueueDepth() {
    return processingQueueDepth;
  }

  public AlertState getCurrentState() {
    return currentState;
  }

  @Override
  public String toString() {
    return "AlertMetrics{"
        + "totalAlertsProcessed="
        + totalAlertsProcessed
        + ", totalIncidentsCreated="
        + totalIncidentsCreated
        + ", totalIncidentsMerged="
        + totalIncidentsMerged
        + ", totalAlertsSuppressed="
        + totalAlertsSuppressed
        + ", totalCorrelationFailures="
        + totalCorrelationFailures
        + ", totalDroppedAlerts="
        + totalDroppedAlerts
        + ", activeIncidentCount="
        + activeIncidentCount
        + ", processingQueueDepth="
        + processingQueueDepth
        + ", currentState="
        + currentState
        + '}';
  }
}
