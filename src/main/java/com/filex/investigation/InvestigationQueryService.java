package com.filex.investigation;

import com.filex.model.IncidentEntity;
import com.filex.model.IncidentEvidenceEntity;
import com.filex.model.ForensicTimelineEntity;
import com.filex.repository.IncidentRepository;
import com.filex.repository.IncidentEvidenceRepository;
import com.filex.repository.ForensicTimelineRepository;
import com.filex.repository.PageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Orchestrates forensic investigation queries across incidents, evidence, and timeline.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Coordinate investigation queries across repositories</li>
 *   <li>Apply composable filtering criteria</li>
 *   <li>Provide investigation-oriented analysis APIs</li>
 *   <li>Track query performance metrics</li>
 * </ul>
 *
 * <p>Design principles:
 * <ul>
 *   <li>Repositories remain persistence-only (no SQL outside repositories)</li>
 *   <li>Investigation logic isolated from UI</li>
 *   <li>Immutable investigation results</li>
 *   <li>Query failures never mutate forensic state</li>
 * </ul>
 *
 * <p>Thread-safety: NOT thread-safe. Create separate instances per thread or
 * synchronize externally.
 */
public final class InvestigationQueryService {

    private static final Logger log = LoggerFactory.getLogger(InvestigationQueryService.class);

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 1000;
    private static final int OVERSIZED_RESULT_THRESHOLD = 500;

    private final Connection connection;
    private final IncidentRepository incidentRepository;
    private final IncidentEvidenceRepository evidenceRepository;
    private final ForensicTimelineRepository timelineRepository;
    private final InvestigationMetrics metrics;

    public InvestigationQueryService(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection must not be null");
        this.incidentRepository = new IncidentRepository(connection);
        this.evidenceRepository = new IncidentEvidenceRepository(connection);
        this.timelineRepository = new ForensicTimelineRepository(connection);
        this.metrics = new InvestigationMetrics();
    }

    // -------------------------------------------------------------------------
    // Incident Investigation
    // -------------------------------------------------------------------------

    /**
     * Finds incidents matching the investigation criteria.
     *
     * @param criteria filtering criteria
     * @param pageNumber page number (0-indexed)
     * @param pageSize page size (max 1000)
     * @return investigation result with matching incidents
     * @throws InvestigationException if query fails
     */
    public InvestigationResult<IncidentSummary> findIncidents(
            InvestigationCriteria criteria,
            int pageNumber,
            int pageSize) throws InvestigationException {

        Objects.requireNonNull(criteria, "criteria must not be null");
        validatePagination(pageNumber, pageSize);

        long startTime = System.currentTimeMillis();
        try {
            metrics.recordIncidentQuery();

            List<IncidentEntity> incidents = queryIncidents(criteria, pageNumber, pageSize);
            List<IncidentSummary> summaries = new ArrayList<>();

            for (IncidentEntity incident : incidents) {
                long evidenceCount = evidenceRepository.countByIncidentId(incident.getIncidentId());
                summaries.add(toIncidentSummary(incident, evidenceCount));
            }

            long totalCount = countIncidents(criteria);
            long duration = System.currentTimeMillis() - startTime;
            metrics.recordQuery(duration);

            if (summaries.size() > OVERSIZED_RESULT_THRESHOLD) {
                metrics.recordOversizedResult();
                log.warn("Oversized incident query result: {} items (threshold: {})",
                        summaries.size(), OVERSIZED_RESULT_THRESHOLD);
            }

            return InvestigationResult.<IncidentSummary>builder()
                    .items(summaries)
                    .totalCount(totalCount)
                    .pageNumber(pageNumber)
                    .pageSize(pageSize)
                    .queryTimestamp(Instant.now())
                    .queryDurationMs(duration)
                    .hasMore((pageNumber + 1) * pageSize < totalCount)
                    .build();

        } catch (SQLException e) {
            metrics.recordFailedQuery();
            log.error("Failed to query incidents: {}", e.getMessage(), e);
            throw new InvestigationException("Failed to query incidents", e);
        }
    }

    /**
     * Finds a single incident by ID with full details.
     *
     * @param incidentId incident ID
     * @return incident summary if found
     * @throws InvestigationException if query fails
     */
    public Optional<IncidentSummary> findIncidentById(String incidentId) throws InvestigationException {
        Objects.requireNonNull(incidentId, "incidentId must not be null");

        long startTime = System.currentTimeMillis();
        try {
            metrics.recordIncidentQuery();

            Optional<IncidentEntity> incident = incidentRepository.findByIncidentId(incidentId);
            if (incident.isEmpty()) {
                return Optional.empty();
            }

            long evidenceCount = evidenceRepository.countByIncidentId(incidentId);
            IncidentSummary summary = toIncidentSummary(incident.get(), evidenceCount);

            long duration = System.currentTimeMillis() - startTime;
            metrics.recordQuery(duration);

            return Optional.of(summary);

        } catch (SQLException e) {
            metrics.recordFailedQuery();
            log.error("Failed to find incident by ID: {}", incidentId, e);
            throw new InvestigationException("Failed to find incident: " + incidentId, e);
        }
    }

    // -------------------------------------------------------------------------
    // Evidence Investigation
    // -------------------------------------------------------------------------

    /**
     * Finds evidence for a specific incident.
     *
     * @param incidentId incident ID
     * @return investigation result with evidence
     * @throws InvestigationException if query fails
     */
    public InvestigationResult<EvidenceSummary> findEvidenceForIncident(String incidentId)
            throws InvestigationException {

        Objects.requireNonNull(incidentId, "incidentId must not be null");

        long startTime = System.currentTimeMillis();
        try {
            metrics.recordEvidenceQuery();

            List<IncidentEvidenceEntity> evidence = evidenceRepository.findByIncidentId(incidentId);
            List<EvidenceSummary> summaries = evidence.stream()
                    .map(this::toEvidenceSummary)
                    .toList();

            long duration = System.currentTimeMillis() - startTime;
            metrics.recordQuery(duration);

            return InvestigationResult.<EvidenceSummary>builder()
                    .items(summaries)
                    .totalCount(summaries.size())
                    .pageNumber(0)
                    .pageSize(summaries.size())
                    .queryTimestamp(Instant.now())
                    .queryDurationMs(duration)
                    .hasMore(false)
                    .build();

        } catch (SQLException e) {
            metrics.recordFailedQuery();
            log.error("Failed to find evidence for incident: {}", incidentId, e);
            throw new InvestigationException("Failed to find evidence for incident: " + incidentId, e);
        }
    }

    /**
     * Finds evidence matching the investigation criteria.
     *
     * @param criteria filtering criteria
     * @param pageNumber page number (0-indexed)
     * @param pageSize page size (max 1000)
     * @return investigation result with matching evidence
     * @throws InvestigationException if query fails
     */
    public InvestigationResult<EvidenceSummary> findEvidence(
            InvestigationCriteria criteria,
            int pageNumber,
            int pageSize) throws InvestigationException {

        Objects.requireNonNull(criteria, "criteria must not be null");
        validatePagination(pageNumber, pageSize);

        long startTime = System.currentTimeMillis();
        try {
            metrics.recordEvidenceQuery();

            List<IncidentEvidenceEntity> evidence = queryEvidence(criteria, pageNumber, pageSize);
            List<EvidenceSummary> summaries = evidence.stream()
                    .map(this::toEvidenceSummary)
                    .toList();

            long totalCount = countEvidence(criteria);
            long duration = System.currentTimeMillis() - startTime;
            metrics.recordQuery(duration);

            if (summaries.size() > OVERSIZED_RESULT_THRESHOLD) {
                metrics.recordOversizedResult();
                log.warn("Oversized evidence query result: {} items", summaries.size());
            }

            return InvestigationResult.<EvidenceSummary>builder()
                    .items(summaries)
                    .totalCount(totalCount)
                    .pageNumber(pageNumber)
                    .pageSize(pageSize)
                    .queryTimestamp(Instant.now())
                    .queryDurationMs(duration)
                    .hasMore((pageNumber + 1) * pageSize < totalCount)
                    .build();

        } catch (SQLException e) {
            metrics.recordFailedQuery();
            log.error("Failed to query evidence: {}", e.getMessage(), e);
            throw new InvestigationException("Failed to query evidence", e);
        }
    }

    // -------------------------------------------------------------------------
    // Timeline Investigation
    // -------------------------------------------------------------------------

    /**
     * Finds timeline events for a specific incident.
     *
     * @param incidentId incident ID
     * @return investigation result with timeline events
     * @throws InvestigationException if query fails
     */
    public InvestigationResult<TimelineEventSummary> findTimelineForIncident(String incidentId)
            throws InvestigationException {

        Objects.requireNonNull(incidentId, "incidentId must not be null");

        long startTime = System.currentTimeMillis();
        try {
            metrics.recordTimelineQuery();

            List<ForensicTimelineEntity> timeline = timelineRepository.findByIncidentId(incidentId);
            List<TimelineEventSummary> summaries = timeline.stream()
                    .map(this::toTimelineEventSummary)
                    .toList();

            long duration = System.currentTimeMillis() - startTime;
            metrics.recordQuery(duration);

            return InvestigationResult.<TimelineEventSummary>builder()
                    .items(summaries)
                    .totalCount(summaries.size())
                    .pageNumber(0)
                    .pageSize(summaries.size())
                    .queryTimestamp(Instant.now())
                    .queryDurationMs(duration)
                    .hasMore(false)
                    .build();

        } catch (SQLException e) {
            metrics.recordFailedQuery();
            log.error("Failed to find timeline for incident: {}", incidentId, e);
            throw new InvestigationException("Failed to find timeline for incident: " + incidentId, e);
        }
    }

    /**
     * Finds timeline events matching the investigation criteria.
     *
     * @param criteria filtering criteria
     * @param pageNumber page number (0-indexed)
     * @param pageSize page size (max 1000)
     * @return investigation result with matching timeline events
     * @throws InvestigationException if query fails
     */
    public InvestigationResult<TimelineEventSummary> findTimelineEvents(
            InvestigationCriteria criteria,
            int pageNumber,
            int pageSize) throws InvestigationException {

        Objects.requireNonNull(criteria, "criteria must not be null");
        validatePagination(pageNumber, pageSize);

        long startTime = System.currentTimeMillis();
        try {
            metrics.recordTimelineQuery();

            List<ForensicTimelineEntity> timeline = queryTimeline(criteria, pageNumber, pageSize);
            List<TimelineEventSummary> summaries = timeline.stream()
                    .map(this::toTimelineEventSummary)
                    .toList();

            long totalCount = countTimeline(criteria);
            long duration = System.currentTimeMillis() - startTime;
            metrics.recordQuery(duration);

            if (summaries.size() > OVERSIZED_RESULT_THRESHOLD) {
                metrics.recordOversizedResult();
                log.warn("Oversized timeline query result: {} items", summaries.size());
            }

            return InvestigationResult.<TimelineEventSummary>builder()
                    .items(summaries)
                    .totalCount(totalCount)
                    .pageNumber(pageNumber)
                    .pageSize(pageSize)
                    .queryTimestamp(Instant.now())
                    .queryDurationMs(duration)
                    .hasMore((pageNumber + 1) * pageSize < totalCount)
                    .build();

        } catch (SQLException e) {
            metrics.recordFailedQuery();
            log.error("Failed to query timeline: {}", e.getMessage(), e);
            throw new InvestigationException("Failed to query timeline", e);
        }
    }

    // -------------------------------------------------------------------------
    // Correlation Exploration
    // -------------------------------------------------------------------------

    /**
     * Finds related incidents by correlation ID.
     *
     * @param correlationId correlation ID
     * @return investigation result with related incidents
     * @throws InvestigationException if query fails
     */
    public InvestigationResult<IncidentSummary> findRelatedIncidents(String correlationId)
            throws InvestigationException {

        Objects.requireNonNull(correlationId, "correlationId must not be null");

        long startTime = System.currentTimeMillis();
        try {
            metrics.recordCorrelationTraversal();

            List<IncidentEntity> incidents = incidentRepository.findByCorrelationId(correlationId);
            List<IncidentSummary> summaries = new ArrayList<>();

            for (IncidentEntity incident : incidents) {
                long evidenceCount = evidenceRepository.countByIncidentId(incident.getIncidentId());
                summaries.add(toIncidentSummary(incident, evidenceCount));
            }

            long duration = System.currentTimeMillis() - startTime;
            metrics.recordQuery(duration);

            return InvestigationResult.<IncidentSummary>builder()
                    .items(summaries)
                    .totalCount(summaries.size())
                    .pageNumber(0)
                    .pageSize(summaries.size())
                    .queryTimestamp(Instant.now())
                    .queryDurationMs(duration)
                    .hasMore(false)
                    .build();

        } catch (SQLException e) {
            metrics.recordFailedQuery();
            log.error("Failed to find related incidents: {}", correlationId, e);
            throw new InvestigationException("Failed to find related incidents: " + correlationId, e);
        }
    }

    // -------------------------------------------------------------------------
    // Metrics
    // -------------------------------------------------------------------------

    /**
     * Returns a snapshot of investigation metrics.
     */
    public InvestigationMetrics.InvestigationMetricsSnapshot getMetrics() {
        return metrics.snapshot();
    }

    // -------------------------------------------------------------------------
    // Private helpers - Query execution
    // -------------------------------------------------------------------------

    private List<IncidentEntity> queryIncidents(InvestigationCriteria criteria, int pageNumber, int pageSize)
            throws SQLException {

        PageRequest pageRequest = new PageRequest(pageNumber, pageSize);

        // Step 1: Apply primary filter (most selective)
        List<IncidentEntity> incidents;
        
        if (criteria.hasTimeRangeFilter()) {
            incidents = incidentRepository.findByTimeRange(
                    criteria.getStartTime(),
                    criteria.getEndTime(),
                    pageRequest
            ).content();
        } else if (criteria.hasCorrelationFilter()) {
            incidents = incidentRepository.findByCorrelationId(criteria.getCorrelationId());
            incidents = paginateList(incidents, pageNumber, pageSize);
        } else if (criteria.hasStatusFilter() && criteria.getStatuses().size() == 1) {
            String status = criteria.getStatuses().iterator().next();
            incidents = incidentRepository.findByStatus(status);
            incidents = paginateList(incidents, pageNumber, pageSize);
        } else {
            incidents = incidentRepository.findAll(pageRequest).content();
        }

        // Step 2: Apply secondary filters in-memory
        incidents = applySecondaryIncidentFilters(incidents, criteria);
        
        return incidents;
    }

    /**
     * Applies secondary filters to incidents after primary query.
     * This enables multi-criteria filtering (e.g., time range + status).
     */
    private List<IncidentEntity> applySecondaryIncidentFilters(List<IncidentEntity> incidents, 
                                                                InvestigationCriteria criteria) {
        List<IncidentEntity> filtered = incidents;

        // Apply status filter if not already used as primary
        if (criteria.hasStatusFilter()) {
            filtered = filtered.stream()
                    .filter(inc -> criteria.getStatuses().contains(inc.getStatus()))
                    .toList();
        }

        // Apply severity filter
        if (criteria.hasSeverityFilter()) {
            filtered = filtered.stream()
                    .filter(inc -> criteria.getSeverities().contains(inc.getSeverity()))
                    .toList();
        }

        // Apply confidence filter
        if (criteria.hasConfidenceFilter()) {
            filtered = filtered.stream()
                    .filter(inc -> criteria.getConfidences().contains(inc.getConfidence()))
                    .toList();
        }

        return filtered;
    }

    private long countIncidents(InvestigationCriteria criteria) throws SQLException {
        // For accurate counts with multi-criteria, we need to apply all filters
        // This is less efficient but ensures correct pagination metadata
        
        List<IncidentEntity> allIncidents;
        
        if (criteria.hasTimeRangeFilter()) {
            // Get all incidents in time range using chunked pagination
            allIncidents = new ArrayList<>();
            int page = 0;
            int pageSize = 1000;
            while (true) {
                PageRequest pageRequest = new PageRequest(page, pageSize);
                List<IncidentEntity> chunk = incidentRepository.findByTimeRange(
                        criteria.getStartTime(),
                        criteria.getEndTime(),
                        pageRequest
                ).content();
                allIncidents.addAll(chunk);
                if (chunk.size() < pageSize) {
                    break;
                }
                page++;
            }
        } else if (criteria.hasCorrelationFilter()) {
            allIncidents = incidentRepository.findByCorrelationId(criteria.getCorrelationId());
        } else if (criteria.hasStatusFilter() && criteria.getStatuses().size() == 1) {
            String status = criteria.getStatuses().iterator().next();
            allIncidents = incidentRepository.findByStatus(status);
        } else {
            return incidentRepository.count();
        }

        // Apply secondary filters
        allIncidents = applySecondaryIncidentFilters(allIncidents, criteria);
        
        return allIncidents.size();
    }

    private List<IncidentEvidenceEntity> queryEvidence(InvestigationCriteria criteria, int pageNumber, int pageSize)
            throws SQLException {

        PageRequest pageRequest = new PageRequest(pageNumber, pageSize);

        // Step 1: Apply primary filter
        List<IncidentEvidenceEntity> evidence;
        
        if (criteria.hasRuleNameFilter() && criteria.getRuleNames().size() == 1) {
            String ruleName = criteria.getRuleNames().iterator().next();
            evidence = evidenceRepository.findByRuleName(ruleName, pageRequest).content();
        } else if (criteria.hasPathFilter()) {
            evidence = evidenceRepository.findByFilePath(criteria.getPathPattern(), pageRequest).content();
        } else if (criteria.hasCorrelationFilter()) {
            evidence = evidenceRepository.findByCorrelationId(criteria.getCorrelationId());
            evidence = paginateList(evidence, pageNumber, pageSize);
        } else {
            // Default: return empty (evidence queries require specific criteria)
            return List.of();
        }

        // Step 2: Apply secondary filters in-memory
        evidence = applySecondaryEvidenceFilters(evidence, criteria);
        
        return evidence;
    }

    /**
     * Applies secondary filters to evidence after primary query.
     */
    private List<IncidentEvidenceEntity> applySecondaryEvidenceFilters(List<IncidentEvidenceEntity> evidence,
                                                                        InvestigationCriteria criteria) {
        List<IncidentEvidenceEntity> filtered = evidence;

        // Apply severity filter
        if (criteria.hasSeverityFilter()) {
            filtered = filtered.stream()
                    .filter(ev -> criteria.getSeverities().contains(ev.getSeverity()))
                    .toList();
        }

        // Apply confidence filter
        if (criteria.hasConfidenceFilter()) {
            filtered = filtered.stream()
                    .filter(ev -> criteria.getConfidences().contains(ev.getConfidence()))
                    .toList();
        }

        return filtered;
    }

    private long countEvidence(InvestigationCriteria criteria) throws SQLException {
        // For accurate counts with multi-criteria, apply all filters
        
        List<IncidentEvidenceEntity> allEvidence;
        
        if (criteria.hasRuleNameFilter() && criteria.getRuleNames().size() == 1) {
            String ruleName = criteria.getRuleNames().iterator().next();
            // Get all evidence using chunked pagination
            allEvidence = new ArrayList<>();
            int page = 0;
            int pageSize = 1000;
            while (true) {
                PageRequest pageRequest = new PageRequest(page, pageSize);
                List<IncidentEvidenceEntity> chunk = evidenceRepository.findByRuleName(ruleName, pageRequest).content();
                allEvidence.addAll(chunk);
                if (chunk.size() < pageSize) {
                    break;
                }
                page++;
            }
        } else if (criteria.hasPathFilter()) {
            // Get all evidence using chunked pagination
            allEvidence = new ArrayList<>();
            int page = 0;
            int pageSize = 1000;
            while (true) {
                PageRequest pageRequest = new PageRequest(page, pageSize);
                List<IncidentEvidenceEntity> chunk = evidenceRepository.findByFilePath(criteria.getPathPattern(), pageRequest).content();
                allEvidence.addAll(chunk);
                if (chunk.size() < pageSize) {
                    break;
                }
                page++;
            }
        } else if (criteria.hasCorrelationFilter()) {
            allEvidence = evidenceRepository.findByCorrelationId(criteria.getCorrelationId());
        } else {
            return 0;
        }

        // Apply secondary filters
        allEvidence = applySecondaryEvidenceFilters(allEvidence, criteria);
        
        return allEvidence.size();
    }

    private List<ForensicTimelineEntity> queryTimeline(InvestigationCriteria criteria, int pageNumber, int pageSize)
            throws SQLException {

        PageRequest pageRequest = new PageRequest(pageNumber, pageSize);

        // Step 1: Apply primary filter
        List<ForensicTimelineEntity> timeline;
        
        if (criteria.hasTimeRangeFilter()) {
            timeline = timelineRepository.findByTimeRange(
                    criteria.getStartTime(),
                    criteria.getEndTime(),
                    pageRequest
            ).content();
        } else if (criteria.hasEventTypeFilter() && criteria.getEventTypes().size() == 1) {
            String eventType = criteria.getEventTypes().iterator().next();
            timeline = timelineRepository.findByEventType(eventType, pageRequest).content();
        } else if (criteria.hasSeverityFilter() && criteria.getSeverities().size() == 1) {
            String severity = criteria.getSeverities().iterator().next();
            timeline = timelineRepository.findBySeverity(severity, pageRequest).content();
        } else if (criteria.hasCorrelationFilter()) {
            timeline = timelineRepository.findByCorrelationId(criteria.getCorrelationId());
            timeline = paginateList(timeline, pageNumber, pageSize);
        } else {
            timeline = timelineRepository.findAll(pageRequest).content();
        }

        // Step 2: Apply secondary filters in-memory
        timeline = applySecondaryTimelineFilters(timeline, criteria);
        
        return timeline;
    }

    /**
     * Applies secondary filters to timeline events after primary query.
     */
    private List<ForensicTimelineEntity> applySecondaryTimelineFilters(List<ForensicTimelineEntity> timeline,
                                                                        InvestigationCriteria criteria) {
        List<ForensicTimelineEntity> filtered = timeline;

        // Apply severity filter if not already used as primary
        if (criteria.hasSeverityFilter()) {
            filtered = filtered.stream()
                    .filter(tl -> criteria.getSeverities().contains(tl.getSeverity()))
                    .toList();
        }

        // Apply event type filter if not already used as primary
        if (criteria.hasEventTypeFilter()) {
            filtered = filtered.stream()
                    .filter(tl -> criteria.getEventTypes().contains(tl.getEventType()))
                    .toList();
        }

        return filtered;
    }

    private long countTimeline(InvestigationCriteria criteria) throws SQLException {
        // For accurate counts with multi-criteria, apply all filters
        
        List<ForensicTimelineEntity> allTimeline;
        
        if (criteria.hasTimeRangeFilter()) {
            // Get all timeline using chunked pagination
            allTimeline = new ArrayList<>();
            int page = 0;
            int pageSize = 1000;
            while (true) {
                PageRequest pageRequest = new PageRequest(page, pageSize);
                List<ForensicTimelineEntity> chunk = timelineRepository.findByTimeRange(
                        criteria.getStartTime(),
                        criteria.getEndTime(),
                        pageRequest
                ).content();
                allTimeline.addAll(chunk);
                if (chunk.size() < pageSize) {
                    break;
                }
                page++;
            }
        } else if (criteria.hasEventTypeFilter() && criteria.getEventTypes().size() == 1) {
            String eventType = criteria.getEventTypes().iterator().next();
            // Get all timeline using chunked pagination
            allTimeline = new ArrayList<>();
            int page = 0;
            int pageSize = 1000;
            while (true) {
                PageRequest pageRequest = new PageRequest(page, pageSize);
                List<ForensicTimelineEntity> chunk = timelineRepository.findByEventType(eventType, pageRequest).content();
                allTimeline.addAll(chunk);
                if (chunk.size() < pageSize) {
                    break;
                }
                page++;
            }
        } else if (criteria.hasSeverityFilter() && criteria.getSeverities().size() == 1) {
            String severity = criteria.getSeverities().iterator().next();
            // Get all timeline using chunked pagination
            allTimeline = new ArrayList<>();
            int page = 0;
            int pageSize = 1000;
            while (true) {
                PageRequest pageRequest = new PageRequest(page, pageSize);
                List<ForensicTimelineEntity> chunk = timelineRepository.findBySeverity(severity, pageRequest).content();
                allTimeline.addAll(chunk);
                if (chunk.size() < pageSize) {
                    break;
                }
                page++;
            }
        } else if (criteria.hasCorrelationFilter()) {
            allTimeline = timelineRepository.findByCorrelationId(criteria.getCorrelationId());
        } else {
            return timelineRepository.count();
        }

        // Apply secondary filters
        allTimeline = applySecondaryTimelineFilters(allTimeline, criteria);
        
        return allTimeline.size();
    }

    // -------------------------------------------------------------------------
    // Private helpers - Mapping
    // -------------------------------------------------------------------------

    private IncidentSummary toIncidentSummary(IncidentEntity incident, long evidenceCount) {
        return IncidentSummary.builder()
                .incidentId(incident.getIncidentId())
                .severity(incident.getSeverity())
                .confidence(incident.getConfidence())
                .status(incident.getStatus())
                .title(incident.getTitle())
                .createdAt(incident.getCreatedAt())
                .updatedAt(incident.getUpdatedAt())
                .lastSeenAt(incident.getLastSeenAt())
                .correlationId(incident.getCorrelationId())
                .escalationLevel(incident.getEscalationLevel())
                .detectionCount(incident.getDetectionCount())
                .evidenceCount(evidenceCount)
                .build();
    }

    private EvidenceSummary toEvidenceSummary(IncidentEvidenceEntity evidence) {
        return EvidenceSummary.builder()
                .evidenceId(evidence.getEvidenceId())
                .incidentId(evidence.getIncidentId())
                .detectionId(evidence.getDetectionId())
                .ruleName(evidence.getRuleName())
                .severity(evidence.getSeverity())
                .confidence(evidence.getConfidence())
                .filePath(evidence.getFilePath())
                .detectedAt(evidence.getDetectedAt())
                .correlationId(evidence.getCorrelationId())
                .build();
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

    // -------------------------------------------------------------------------
    // Private helpers - Utilities
    // -------------------------------------------------------------------------

    private void validatePagination(int pageNumber, int pageSize) throws InvestigationException {
        if (pageNumber < 0) {
            throw new InvestigationException("pageNumber must be non-negative");
        }
        if (pageSize <= 0) {
            throw new InvestigationException("pageSize must be positive");
        }
        if (pageSize > MAX_PAGE_SIZE) {
            throw new InvestigationException("pageSize exceeds maximum: " + MAX_PAGE_SIZE);
        }
    }

    private <T> List<T> paginateList(List<T> list, int pageNumber, int pageSize) {
        int start = pageNumber * pageSize;
        if (start >= list.size()) {
            return List.of();
        }
        int end = Math.min(start + pageSize, list.size());
        return list.subList(start, end);
    }
}
