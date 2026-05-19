package com.filex.workspace;

import com.filex.investigation.*;
import com.filex.investigation.replay.ReplayCursor;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orchestrates investigation workspace operations.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Coordinate investigation queries (async, off JavaFX thread)
 *   <li>Manage workspace state transitions
 *   <li>Synchronize state across UI panels
 *   <li>Track workspace metrics
 * </ul>
 *
 * <p>Design principles:
 *
 * <ul>
 *   <li><b>NO direct repository access</b> — uses investigation services only
 *   <li><b>Async queries</b> — never blocks JavaFX UI thread
 *   <li><b>State synchronization</b> — atomic state transitions
 *   <li><b>Failure isolation</b> — query failures don't corrupt workspace state
 * </ul>
 *
 * <p>Thread-safety: Methods are thread-safe. State updates are synchronized. Async queries execute
 * on background thread pool.
 */
public final class WorkspaceService {

  private static final Logger log = LoggerFactory.getLogger(WorkspaceService.class);

  private final InvestigationQueryService queryService;
  private final ReplayNavigationService replayService;
  private final WorkspaceMetrics metrics;
  private final ExecutorService queryExecutor;
  private final WorkspaceSession session;

  private volatile WorkspaceState currentState;
  private final NavigationHistory navigationHistory;
  private final Object stateLock = new Object();

  // Generation counters for stale async response protection
  private final AtomicLong incidentQueryGeneration = new AtomicLong(0);
  private final AtomicLong replayQueryGeneration = new AtomicLong(0);
  private final AtomicLong evidenceQueryGeneration = new AtomicLong(0);
  private final AtomicLong correlationQueryGeneration = new AtomicLong(0);

  /**
   * Creates a workspace service with investigation services.
   *
   * @param queryService investigation query service
   * @param replayService replay navigation service
   * @param dataDirectory data directory for session persistence
   */
  public WorkspaceService(
      InvestigationQueryService queryService,
      ReplayNavigationService replayService,
      Path dataDirectory) {
    this.queryService = Objects.requireNonNull(queryService, "queryService must not be null");
    this.replayService = Objects.requireNonNull(replayService, "replayService must not be null");
    this.metrics = new WorkspaceMetrics();
    this.queryExecutor =
        Executors.newFixedThreadPool(
            4,
            r -> {
              Thread t = new Thread(r, "workspace-query-" + System.nanoTime());
              t.setDaemon(true);
              return t;
            });
    this.session = new WorkspaceSession(dataDirectory);

    // Try to restore session
    WorkspaceState initialState;
    try {
      Optional<WorkspaceState> restored = session.restore();
      if (restored.isPresent()) {
        initialState = restored.get();
        log.info("Workspace session restored from previous session");
        metrics.recordSessionRestoration(0);
      } else {
        initialState = WorkspaceState.builder().build();
      }
    } catch (WorkspaceException e) {
      log.warn("Failed to restore session, starting fresh", e);
      initialState = WorkspaceState.builder().build();
      metrics.recordSessionRestorationFailure();
    }

    this.currentState = initialState;
    this.navigationHistory = new NavigationHistory(initialState);

    log.info("WorkspaceService initialized with async query executor (4 threads)");
  }

  // -------------------------------------------------------------------------
  // State management
  // -------------------------------------------------------------------------

  /** Returns the current workspace state snapshot. Thread-safe. */
  public WorkspaceState currentState() {
    synchronized (stateLock) {
      return currentState;
    }
  }

  /**
   * Updates workspace state atomically. Notifies state change listeners and saves session.
   *
   * @param newState the new workspace state
   */
  public void updateState(WorkspaceState newState) {
    Objects.requireNonNull(newState, "newState must not be null");

    synchronized (stateLock) {
      WorkspaceState oldState = currentState;
      currentState = newState;
      navigationHistory.navigateTo(newState);
      metrics.recordStateSynchronization();

      // Save session asynchronously
      saveSessionAsync(newState);

      log.debug(
          "Workspace state updated: {} → {}",
          oldState.navigationContext(),
          newState.navigationContext());
    }
  }

  /**
   * Transitions to a new navigation context. Updates workspace state atomically.
   *
   * @param context the new navigation context
   */
  public void transitionTo(NavigationContext context) {
    Objects.requireNonNull(context, "context must not be null");

    long startTime = System.currentTimeMillis();

    synchronized (stateLock) {
      WorkspaceState newState =
          currentState.toBuilder()
              .navigationContext(context)
              .navigationDepth(currentState.navigationDepth() + 1)
              .build();
      currentState = newState;
      navigationHistory.navigateTo(newState);

      long latency = System.currentTimeMillis() - startTime;
      metrics.recordNavigation(latency);

      // Save session asynchronously
      saveSessionAsync(newState);

      log.info("Navigation transition: {} (depth: {})", context, newState.navigationDepth());
    }
  }

  /**
   * Navigates back to previous workspace state.
   *
   * @return true if navigation succeeded
   */
  public boolean navigateBack() {
    synchronized (stateLock) {
      Optional<WorkspaceState> previous = navigationHistory.navigateBack();
      if (previous.isPresent()) {
        currentState = previous.get();
        metrics.recordNavigation(0);
        saveSessionAsync(currentState);
        log.info("Navigated back to: {}", currentState.navigationContext());
        return true;
      }
      return false;
    }
  }

  /**
   * Navigates forward to next workspace state.
   *
   * @return true if navigation succeeded
   */
  public boolean navigateForward() {
    synchronized (stateLock) {
      Optional<WorkspaceState> next = navigationHistory.navigateForward();
      if (next.isPresent()) {
        currentState = next.get();
        metrics.recordNavigation(0);
        saveSessionAsync(currentState);
        log.info("Navigated forward to: {}", currentState.navigationContext());
        return true;
      }
      return false;
    }
  }

  /** Returns true if back navigation is available. */
  public boolean canNavigateBack() {
    synchronized (stateLock) {
      return navigationHistory.canNavigateBack();
    }
  }

  /** Returns true if forward navigation is available. */
  public boolean canNavigateForward() {
    synchronized (stateLock) {
      return navigationHistory.canNavigateForward();
    }
  }

  /** Saves session asynchronously (non-blocking). */
  private void saveSessionAsync(WorkspaceState state) {
    CompletableFuture.runAsync(
        () -> {
          synchronized (stateLock) {
            try {
              session.save(state);
            } catch (WorkspaceException e) {
              log.warn("Failed to save workspace session", e);
            }
          }
        },
        queryExecutor);
  }

  // -------------------------------------------------------------------------
  // Async investigation queries
  // -------------------------------------------------------------------------

  /**
   * Finds incidents matching criteria asynchronously. Executes on background thread, never blocks
   * UI.
   *
   * @param criteria investigation criteria
   * @param pageNumber page number (0-indexed)
   * @param pageSize page size
   * @param onSuccess callback on success (called on JavaFX thread)
   * @param onFailure callback on failure (called on JavaFX thread)
   */
  public void findIncidentsAsync(
      InvestigationCriteria criteria,
      int pageNumber,
      int pageSize,
      Consumer<InvestigationResult<IncidentSummary>> onSuccess,
      Consumer<Throwable> onFailure) {

    metrics.recordAsyncQueryDispatched();

    // Capture current generation - this request is valid only for this generation
    final long requestGeneration = incidentQueryGeneration.incrementAndGet();

    CompletableFuture.supplyAsync(
            () -> {
              try {
                return queryService.findIncidents(criteria, pageNumber, pageSize);
              } catch (InvestigationException e) {
                throw new RuntimeException("Failed to query incidents", e);
              }
            },
            queryExecutor)
        .whenComplete(
            (result, error) -> {
              javafx.application.Platform.runLater(
                  () -> {
                    // Validate generation - discard stale responses
                    if (incidentQueryGeneration.get() != requestGeneration) {
                      metrics.recordStaleAsyncResponseIgnored();
                      log.debug(
                          "Discarding stale incident query response (generation {} vs current {})",
                          requestGeneration,
                          incidentQueryGeneration.get());
                      return;
                    }

                    if (error != null) {
                      metrics.recordAsyncQueryFailed();
                      log.error("Async incident query failed", error);
                      onFailure.accept(error);
                    } else {
                      metrics.recordAsyncQueryCompleted();
                      log.debug(
                          "Async incident query completed: {} results", result.getItemCount());
                      onSuccess.accept(result);
                    }
                  });
            });
  }

  /**
   * Finds a single incident by ID asynchronously.
   *
   * @param incidentId incident ID
   * @param onSuccess callback on success
   * @param onFailure callback on failure
   */
  public void findIncidentByIdAsync(
      String incidentId,
      Consumer<Optional<IncidentSummary>> onSuccess,
      Consumer<Throwable> onFailure) {

    metrics.recordAsyncQueryDispatched();

    final long requestGeneration = incidentQueryGeneration.incrementAndGet();

    CompletableFuture.supplyAsync(
            () -> {
              try {
                return queryService.findIncidentById(incidentId);
              } catch (InvestigationException e) {
                throw new RuntimeException("Failed to find incident: " + incidentId, e);
              }
            },
            queryExecutor)
        .whenComplete(
            (result, error) -> {
              javafx.application.Platform.runLater(
                  () -> {
                    if (incidentQueryGeneration.get() != requestGeneration) {
                      metrics.recordStaleAsyncResponseIgnored();
                      log.debug("Discarding stale incident lookup response for {}", incidentId);
                      return;
                    }

                    if (error != null) {
                      metrics.recordAsyncQueryFailed();
                      log.error("Async incident lookup failed: {}", incidentId, error);
                      onFailure.accept(error);
                    } else {
                      metrics.recordAsyncQueryCompleted();
                      log.debug("Async incident lookup completed: {}", incidentId);
                      onSuccess.accept(result);
                    }
                  });
            });
  }

  /**
   * Finds evidence for an incident asynchronously.
   *
   * @param incidentId incident ID
   * @param onSuccess callback on success
   * @param onFailure callback on failure
   */
  public void findEvidenceForIncidentAsync(
      String incidentId,
      Consumer<InvestigationResult<EvidenceSummary>> onSuccess,
      Consumer<Throwable> onFailure) {

    metrics.recordAsyncQueryDispatched();

    final long requestGeneration = evidenceQueryGeneration.incrementAndGet();

    CompletableFuture.supplyAsync(
            () -> {
              try {
                return queryService.findEvidenceForIncident(incidentId);
              } catch (InvestigationException e) {
                throw new RuntimeException(
                    "Failed to find evidence for incident: " + incidentId, e);
              }
            },
            queryExecutor)
        .whenComplete(
            (result, error) -> {
              javafx.application.Platform.runLater(
                  () -> {
                    if (evidenceQueryGeneration.get() != requestGeneration) {
                      metrics.recordStaleAsyncResponseIgnored();
                      log.debug("Discarding stale evidence query response for {}", incidentId);
                      return;
                    }

                    if (error != null) {
                      metrics.recordAsyncQueryFailed();
                      log.error("Async evidence query failed: {}", incidentId, error);
                      onFailure.accept(error);
                    } else {
                      metrics.recordAsyncQueryCompleted();
                      log.debug(
                          "Async evidence query completed: {} results", result.getItemCount());
                      onSuccess.accept(result);
                    }
                  });
            });
  }

  /** Finds evidence matching criteria asynchronously. */
  public void findEvidenceAsync(
      InvestigationCriteria criteria,
      int pageNumber,
      int pageSize,
      Consumer<InvestigationResult<EvidenceSummary>> onSuccess,
      Consumer<Throwable> onFailure) {

    metrics.recordAsyncQueryDispatched();

    final long requestGeneration = evidenceQueryGeneration.incrementAndGet();

    CompletableFuture.supplyAsync(
            () -> {
              try {
                return queryService.findEvidence(criteria, pageNumber, pageSize);
              } catch (InvestigationException e) {
                throw new RuntimeException("Failed to find evidence by criteria", e);
              }
            },
            queryExecutor)
        .whenComplete(
            (result, error) -> {
              javafx.application.Platform.runLater(
                  () -> {
                    if (evidenceQueryGeneration.get() != requestGeneration) {
                      metrics.recordStaleAsyncResponseIgnored();
                      return;
                    }

                    if (error != null) {
                      metrics.recordAsyncQueryFailed();
                      onFailure.accept(error);
                    } else {
                      metrics.recordAsyncQueryCompleted();
                      onSuccess.accept(result);
                    }
                  });
            });
  }

  /**
   * Replays incident timeline asynchronously.
   *
   * @param incidentId incident ID
   * @param onSuccess callback on success
   * @param onFailure callback on failure
   */
  public void replayIncidentTimelineAsync(
      String incidentId,
      Consumer<ReplayNavigationService.ReplayWindow> onSuccess,
      Consumer<Throwable> onFailure) {

    metrics.recordAsyncQueryDispatched();

    final long requestGeneration = replayQueryGeneration.incrementAndGet();
    long startTime = System.currentTimeMillis();

    CompletableFuture.supplyAsync(
            () -> {
              try {
                return replayService.replayIncidentTimeline(incidentId);
              } catch (InvestigationException e) {
                throw new RuntimeException("Failed to replay timeline: " + incidentId, e);
              }
            },
            queryExecutor)
        .whenComplete(
            (result, error) -> {
              javafx.application.Platform.runLater(
                  () -> {
                    if (replayQueryGeneration.get() != requestGeneration) {
                      metrics.recordStaleAsyncResponseIgnored();
                      log.debug("Discarding stale replay response for {}", incidentId);
                      return;
                    }

                    if (error != null) {
                      metrics.recordAsyncQueryFailed();
                      metrics.recordReplayInterruption();
                      log.error("Async replay failed: {}", incidentId, error);
                      onFailure.accept(error);
                    } else {
                      metrics.recordAsyncQueryCompleted();
                      long latency = System.currentTimeMillis() - startTime;
                      metrics.recordReplayRender(latency);
                      log.debug(
                          "Async replay completed: {} events in {}ms",
                          result.getEventCount(),
                          latency);
                      onSuccess.accept(result);
                    }
                  });
            });
  }

  /** Replays incident timeline from a checkpoint asynchronously. */
  public void replayFromCheckpointAsync(
      ReplayCursor cursor,
      Consumer<ReplayNavigationService.ReplayWindow> onSuccess,
      Consumer<Throwable> onFailure) {

    metrics.recordAsyncQueryDispatched();

    final long requestGeneration = replayQueryGeneration.incrementAndGet();
    long startTime = System.currentTimeMillis();

    CompletableFuture.supplyAsync(
            () -> {
              try {
                return replayService.replayFromCheckpoint(cursor);
              } catch (InvestigationException e) {
                throw new RuntimeException(
                    "Failed to replay from checkpoint: " + cursor.incidentId(), e);
              }
            },
            queryExecutor)
        .whenComplete(
            (result, error) -> {
              javafx.application.Platform.runLater(
                  () -> {
                    if (replayQueryGeneration.get() != requestGeneration) {
                      metrics.recordStaleAsyncResponseIgnored();
                      log.debug("Discarding stale replay response for {}", cursor.incidentId());
                      return;
                    }

                    if (error != null) {
                      metrics.recordAsyncQueryFailed();
                      metrics.recordReplayInterruption();
                      log.error("Async replay failed: {}", cursor.incidentId(), error);
                      onFailure.accept(error);
                    } else {
                      metrics.recordAsyncQueryCompleted();
                      long latency = System.currentTimeMillis() - startTime;
                      metrics.recordReplayRender(latency);
                      log.debug(
                          "Async replay completed: {} events in {}ms",
                          result.getEventCount(),
                          latency);
                      onSuccess.accept(result);
                    }
                  });
            });
  }

  /**
   * Finds related incidents by correlation ID asynchronously.
   *
   * @param correlationId correlation ID
   * @param onSuccess callback on success
   * @param onFailure callback on failure
   */
  public void findRelatedIncidentsAsync(
      String correlationId,
      Consumer<InvestigationResult<IncidentSummary>> onSuccess,
      Consumer<Throwable> onFailure) {

    metrics.recordAsyncQueryDispatched();

    final long requestGeneration = correlationQueryGeneration.incrementAndGet();

    CompletableFuture.supplyAsync(
            () -> {
              try {
                return queryService.findRelatedIncidents(correlationId);
              } catch (InvestigationException e) {
                throw new RuntimeException("Failed to find related incidents: " + correlationId, e);
              }
            },
            queryExecutor)
        .whenComplete(
            (result, error) -> {
              javafx.application.Platform.runLater(
                  () -> {
                    if (correlationQueryGeneration.get() != requestGeneration) {
                      metrics.recordStaleAsyncResponseIgnored();
                      log.debug(
                          "Discarding stale correlation query response for {}", correlationId);
                      return;
                    }

                    if (error != null) {
                      metrics.recordAsyncQueryFailed();
                      log.error("Async correlation query failed: {}", correlationId, error);
                      onFailure.accept(error);
                    } else {
                      metrics.recordAsyncQueryCompleted();
                      log.debug(
                          "Async correlation query completed: {} results", result.getItemCount());
                      onSuccess.accept(result);
                    }
                  });
            });
  }

  // -------------------------------------------------------------------------
  // Metrics
  // -------------------------------------------------------------------------

  /** Returns a snapshot of workspace metrics. */
  public WorkspaceMetrics.WorkspaceMetricsSnapshot getMetrics() {
    return metrics.snapshot();
  }

  // -------------------------------------------------------------------------
  // Lifecycle
  // -------------------------------------------------------------------------

  /** Shuts down the workspace service. Stops async query executor and releases resources. */
  public void shutdown() {
    log.info("Shutting down WorkspaceService...");

    // Save final session state
    synchronized (stateLock) {
      try {
        session.save(currentState);
        log.info("Final workspace session saved");
      } catch (WorkspaceException e) {
        log.warn("Failed to save final session", e);
      }
    }

    queryExecutor.shutdown();
    log.info("WorkspaceService shutdown complete.");
  }
}
