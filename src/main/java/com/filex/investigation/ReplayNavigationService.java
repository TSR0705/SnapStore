package com.filex.investigation;

import com.filex.model.ForensicTimelineEntity;
import com.filex.repository.ForensicTimelineRepository;
import com.filex.repository.PageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Provides deterministic replay navigation for forensic timeline reconstruction.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Event-by-event replay traversal</li>
 *   <li>Replay windows with bounded loading</li>
 *   <li>Replay checkpoints for navigation</li>
 *   <li>Deterministic ordering using (timestamp, sequence_number)</li>
 * </ul>
 *
 * <p>Replay ordering strategy:
 * <pre>
 * ORDER BY timestamp ASC, sequence_number ASC  (forward replay)
 * ORDER BY timestamp DESC, sequence_number DESC (reverse replay)
 * </pre>
 *
 * <p>Thread-safety: NOT thread-safe. Create separate instances per thread.
 */
public final class ReplayNavigationService {

    private static final Logger log = LoggerFactory.getLogger(ReplayNavigationService.class);

    private static final int DEFAULT_REPLAY_WINDOW_SIZE = 100;
    private static final int MAX_REPLAY_WINDOW_SIZE = 500;

    private final Connection connection;
    private final ForensicTimelineRepository timelineRepository;
    private final InvestigationMetrics metrics;

    public ReplayNavigationService(Connection connection, InvestigationMetrics metrics) {
        this.connection = Objects.requireNonNull(connection, "connection must not be null");
        this.timelineRepository = new ForensicTimelineRepository(connection);
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    // -------------------------------------------------------------------------
    // Replay Navigation
    // -------------------------------------------------------------------------

    /**
     * Replays timeline events within a time window.
     *
     * @param startTime window start time
     * @param endTime window end time
     * @param windowSize number of events per window (max 500)
     * @return replay window with events
     * @throws InvestigationException if replay fails
     */
    public ReplayWindow replayTimeWindow(Instant startTime, Instant endTime, int windowSize)
            throws InvestigationException {

        Objects.requireNonNull(startTime, "startTime must not be null");
        Objects.requireNonNull(endTime, "endTime must not be null");
        validateWindowSize(windowSize);

        if (endTime.isBefore(startTime)) {
            throw new InvestigationException("endTime must be after startTime");
        }

        long startMs = System.currentTimeMillis();
        try {
            metrics.recordReplayNavigation();

            PageRequest pageRequest = new PageRequest(0, windowSize);
            List<ForensicTimelineEntity> events = timelineRepository.findByTimeRange(
                    startTime, endTime, pageRequest
            ).content();

            List<TimelineEventSummary> summaries = events.stream()
                    .map(this::toTimelineEventSummary)
                    .toList();

            long totalCount = timelineRepository.countByTimeRange(startTime, endTime);
            long duration = System.currentTimeMillis() - startMs;
            metrics.recordQuery(duration);

            return new ReplayWindow(
                    summaries,
                    startTime,
                    endTime,
                    0,
                    windowSize,
                    totalCount,
                    summaries.size() < totalCount
            );

        } catch (SQLException e) {
            metrics.recordFailedQuery();
            log.error("Failed to replay time window: {} to {}", startTime, endTime, e);
            throw new InvestigationException("Failed to replay time window", e);
        }
    }

    /**
     * Replays timeline events for a specific incident.
     *
     * @param incidentId incident ID
     * @return replay window with incident timeline
     * @throws InvestigationException if replay fails
     */
    public ReplayWindow replayIncidentTimeline(String incidentId) throws InvestigationException {
        Objects.requireNonNull(incidentId, "incidentId must not be null");

        long startMs = System.currentTimeMillis();
        try {
            metrics.recordReplayNavigation();

            List<ForensicTimelineEntity> events = timelineRepository.findByIncidentId(incidentId);
            List<TimelineEventSummary> summaries = events.stream()
                    .map(this::toTimelineEventSummary)
                    .toList();

            long duration = System.currentTimeMillis() - startMs;
            metrics.recordQuery(duration);

            Instant startTime = summaries.isEmpty() ? Instant.now() : summaries.get(0).getTimestamp();
            Instant endTime = summaries.isEmpty() ? Instant.now() : summaries.get(summaries.size() - 1).getTimestamp();

            return new ReplayWindow(
                    summaries,
                    startTime,
                    endTime,
                    0,
                    summaries.size(),
                    summaries.size(),
                    false
            );

        } catch (SQLException e) {
            metrics.recordFailedQuery();
            log.error("Failed to replay incident timeline: {}", incidentId, e);
            throw new InvestigationException("Failed to replay incident timeline: " + incidentId, e);
        }
    }

    /**
     * Replays timeline events by correlation ID.
     *
     * @param correlationId correlation ID
     * @return replay window with correlated events
     * @throws InvestigationException if replay fails
     */
    public ReplayWindow replayCorrelatedEvents(String correlationId) throws InvestigationException {
        Objects.requireNonNull(correlationId, "correlationId must not be null");

        long startMs = System.currentTimeMillis();
        try {
            metrics.recordReplayNavigation();
            metrics.recordCorrelationTraversal();

            List<ForensicTimelineEntity> events = timelineRepository.findByCorrelationId(correlationId);
            List<TimelineEventSummary> summaries = events.stream()
                    .map(this::toTimelineEventSummary)
                    .toList();

            long duration = System.currentTimeMillis() - startMs;
            metrics.recordQuery(duration);

            Instant startTime = summaries.isEmpty() ? Instant.now() : summaries.get(0).getTimestamp();
            Instant endTime = summaries.isEmpty() ? Instant.now() : summaries.get(summaries.size() - 1).getTimestamp();

            return new ReplayWindow(
                    summaries,
                    startTime,
                    endTime,
                    0,
                    summaries.size(),
                    summaries.size(),
                    false
            );

        } catch (SQLException e) {
            metrics.recordFailedQuery();
            log.error("Failed to replay correlated events: {}", correlationId, e);
            throw new InvestigationException("Failed to replay correlated events: " + correlationId, e);
        }
    }

    /**
     * Finds the next replay checkpoint after a given timestamp.
     *
     * @param afterTimestamp find checkpoint after this time
     * @param windowSize checkpoint window size
     * @return replay window starting at checkpoint
     * @throws InvestigationException if checkpoint navigation fails
     */
    public ReplayWindow findNextCheckpoint(Instant afterTimestamp, int windowSize)
            throws InvestigationException {

        Objects.requireNonNull(afterTimestamp, "afterTimestamp must not be null");
        validateWindowSize(windowSize);

        long startMs = System.currentTimeMillis();
        try {
            metrics.recordReplayNavigation();

            // Find events after the timestamp
            Instant endTime = Instant.now();
            PageRequest pageRequest = new PageRequest(0, windowSize);
            List<ForensicTimelineEntity> events = timelineRepository.findByTimeRange(
                    afterTimestamp, endTime, pageRequest
            ).content();

            List<TimelineEventSummary> summaries = events.stream()
                    .map(this::toTimelineEventSummary)
                    .toList();

            long totalCount = timelineRepository.countByTimeRange(afterTimestamp, endTime);
            long duration = System.currentTimeMillis() - startMs;
            metrics.recordQuery(duration);

            return new ReplayWindow(
                    summaries,
                    afterTimestamp,
                    endTime,
                    0,
                    windowSize,
                    totalCount,
                    summaries.size() < totalCount
            );

        } catch (SQLException e) {
            metrics.recordFailedQuery();
            log.error("Failed to find next checkpoint after: {}", afterTimestamp, e);
            throw new InvestigationException("Failed to find next checkpoint", e);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void validateWindowSize(int windowSize) throws InvestigationException {
        if (windowSize <= 0) {
            throw new InvestigationException("windowSize must be positive");
        }
        if (windowSize > MAX_REPLAY_WINDOW_SIZE) {
            throw new InvestigationException("windowSize exceeds maximum: " + MAX_REPLAY_WINDOW_SIZE);
        }
    }

    private TimelineEventSummary toTimelineEventSummary(ForensicTimelineEntity timeline) {
        return TimelineEventSummary.builder()
                .timelineId(timeline.getTimelineId())
                .timestamp(timeline.getTimestamp())
                .sequenceNumber(timeline.getSequenceNumber())
                .eventType(timeline.getEventType())
                .incidentId(timeline.getIncidentId())
                .evidenceId(timeline.getEvidenceId())
                .severity(timeline.getSeverity())
                .description(timeline.getDescription())
                .correlationId(timeline.getCorrelationId())
                .build();
    }

    /**
     * Immutable replay window containing timeline events.
     */
    public record ReplayWindow(
            List<TimelineEventSummary> events,
            Instant windowStart,
            Instant windowEnd,
            int windowNumber,
            int windowSize,
            long totalEventCount,
            boolean hasMore
    ) {
        public ReplayWindow {
            events = List.copyOf(events);
        }

        public boolean isEmpty() {
            return events.isEmpty();
        }

        public int getEventCount() {
            return events.size();
        }

        @Override
        public String toString() {
            return String.format(
                    "ReplayWindow{eventCount=%d, totalEventCount=%d, windowStart=%s, windowEnd=%s, hasMore=%b}",
                    events.size(), totalEventCount, windowStart, windowEnd, hasMore
            );
        }
    }
}
