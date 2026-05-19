package com.filex.engine;

/**
 * Immutable snapshot of monitoring engine runtime metrics.
 *
 * <p>Provides observability into monitoring state without heavy framework dependencies.
 */
public final class MonitoringMetrics {

  private final int activeWatchCount;
  private final int registeredPathCount;
  private final long totalEventsDetected;
  private final long totalEventsNormalized;
  private final long totalEventsDeduplicated;
  private final long totalOverflowEvents;
  private final long totalInvalidatedWatches;
  private final MonitoringState currentState;

  public MonitoringMetrics(
      int activeWatchCount,
      int registeredPathCount,
      long totalEventsDetected,
      long totalEventsNormalized,
      long totalEventsDeduplicated,
      long totalOverflowEvents,
      long totalInvalidatedWatches,
      MonitoringState currentState) {
    this.activeWatchCount = activeWatchCount;
    this.registeredPathCount = registeredPathCount;
    this.totalEventsDetected = totalEventsDetected;
    this.totalEventsNormalized = totalEventsNormalized;
    this.totalEventsDeduplicated = totalEventsDeduplicated;
    this.totalOverflowEvents = totalOverflowEvents;
    this.totalInvalidatedWatches = totalInvalidatedWatches;
    this.currentState = currentState;
  }

  public int getActiveWatchCount() {
    return activeWatchCount;
  }

  public int getRegisteredPathCount() {
    return registeredPathCount;
  }

  public long getTotalEventsDetected() {
    return totalEventsDetected;
  }

  public long getTotalEventsNormalized() {
    return totalEventsNormalized;
  }

  public long getTotalEventsDeduplicated() {
    return totalEventsDeduplicated;
  }

  public long getTotalOverflowEvents() {
    return totalOverflowEvents;
  }

  public long getTotalInvalidatedWatches() {
    return totalInvalidatedWatches;
  }

  public MonitoringState getCurrentState() {
    return currentState;
  }

  @Override
  public String toString() {
    return "MonitoringMetrics{"
        + "activeWatchCount="
        + activeWatchCount
        + ", registeredPathCount="
        + registeredPathCount
        + ", totalEventsDetected="
        + totalEventsDetected
        + ", totalEventsNormalized="
        + totalEventsNormalized
        + ", totalEventsDeduplicated="
        + totalEventsDeduplicated
        + ", totalOverflowEvents="
        + totalOverflowEvents
        + ", totalInvalidatedWatches="
        + totalInvalidatedWatches
        + ", currentState="
        + currentState
        + '}';
  }
}
