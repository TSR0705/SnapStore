package com.filex.investigation;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe metrics for investigation query operations.
 *
 * <p>Tracks query performance, failure rates, and resource usage for forensic analysis operations.
 *
 * <p>Thread-safety: All operations are atomic and thread-safe.
 */
public final class InvestigationMetrics {

  private final AtomicLong totalQueries = new AtomicLong(0);
  private final AtomicLong failedQueries = new AtomicLong(0);
  private final AtomicLong totalQueryTimeMs = new AtomicLong(0);
  private final AtomicLong incidentQueriesExecuted = new AtomicLong(0);
  private final AtomicLong evidenceQueriesExecuted = new AtomicLong(0);
  private final AtomicLong timelineQueriesExecuted = new AtomicLong(0);
  private final AtomicLong correlationTraversals = new AtomicLong(0);
  private final AtomicLong replayNavigations = new AtomicLong(0);
  private final AtomicLong oversizedResults = new AtomicLong(0);

  public void recordQuery(long durationMs) {
    totalQueries.incrementAndGet();
    totalQueryTimeMs.addAndGet(durationMs);
  }

  public void recordFailedQuery() {
    failedQueries.incrementAndGet();
  }

  public void recordIncidentQuery() {
    incidentQueriesExecuted.incrementAndGet();
  }

  public void recordEvidenceQuery() {
    evidenceQueriesExecuted.incrementAndGet();
  }

  public void recordTimelineQuery() {
    timelineQueriesExecuted.incrementAndGet();
  }

  public void recordCorrelationTraversal() {
    correlationTraversals.incrementAndGet();
  }

  public void recordReplayNavigation() {
    replayNavigations.incrementAndGet();
  }

  public void recordOversizedResult() {
    oversizedResults.incrementAndGet();
  }

  public InvestigationMetricsSnapshot snapshot() {
    return new InvestigationMetricsSnapshot(
        totalQueries.get(),
        failedQueries.get(),
        totalQueryTimeMs.get(),
        incidentQueriesExecuted.get(),
        evidenceQueriesExecuted.get(),
        timelineQueriesExecuted.get(),
        correlationTraversals.get(),
        replayNavigations.get(),
        oversizedResults.get());
  }

  /** Immutable snapshot of investigation metrics. */
  public record InvestigationMetricsSnapshot(
      long totalQueries,
      long failedQueries,
      long totalQueryTimeMs,
      long incidentQueriesExecuted,
      long evidenceQueriesExecuted,
      long timelineQueriesExecuted,
      long correlationTraversals,
      long replayNavigations,
      long oversizedResults) {
    public double getAverageQueryTimeMs() {
      return totalQueries > 0 ? (double) totalQueryTimeMs / totalQueries : 0.0;
    }

    public double getFailureRate() {
      return totalQueries > 0 ? (double) failedQueries / totalQueries : 0.0;
    }

    @Override
    public String toString() {
      return String.format(
          "InvestigationMetrics{totalQueries=%d, failedQueries=%d, avgQueryTimeMs=%.2f, "
              + "incidentQueries=%d, evidenceQueries=%d, timelineQueries=%d, "
              + "correlationTraversals=%d, replayNavigations=%d, oversizedResults=%d}",
          totalQueries,
          failedQueries,
          getAverageQueryTimeMs(),
          incidentQueriesExecuted,
          evidenceQueriesExecuted,
          timelineQueriesExecuted,
          correlationTraversals,
          replayNavigations,
          oversizedResults);
    }
  }
}
