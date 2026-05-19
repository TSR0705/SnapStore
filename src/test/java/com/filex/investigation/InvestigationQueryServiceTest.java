package com.filex.investigation;

import static org.junit.jupiter.api.Assertions.*;

import com.filex.config.AppConfig;
import com.filex.config.ConfigManager;
import com.filex.database.DatabaseManager;
import com.filex.model.ForensicTimelineEntity;
import com.filex.model.IncidentEntity;
import com.filex.model.IncidentEvidenceEntity;
import com.filex.repository.ForensicTimelineRepository;
import com.filex.repository.IncidentEvidenceRepository;
import com.filex.repository.IncidentRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for InvestigationQueryService. */
class InvestigationQueryServiceTest {

  private DatabaseManager dbManager;
  private Path testDbPath;
  private InvestigationQueryService investigationService;
  private IncidentRepository incidentRepo;
  private IncidentEvidenceRepository evidenceRepo;
  private ForensicTimelineRepository timelineRepo;

  @BeforeEach
  void setUp() throws Exception {
    testDbPath = Files.createTempFile("investigation-test-", ".db");
    AppConfig config = createTestConfig(testDbPath);

    dbManager = new DatabaseManager(config);
    dbManager.initialize();

    investigationService = dbManager.investigationQueryService();
    incidentRepo = dbManager.incidentRepository();
    evidenceRepo = dbManager.incidentEvidenceRepository();
    timelineRepo = dbManager.forensicTimelineRepository();
  }

  @AfterEach
  void tearDown() throws Exception {
    if (dbManager != null) {
      dbManager.shutdown();
    }
    if (testDbPath != null && Files.exists(testDbPath)) {
      Files.deleteIfExists(testDbPath);
    }
  }

  @Test
  void testFindIncidentsWithNoCriteria() throws Exception {
    // Create test incidents
    createTestIncident("INC-001", "HIGH", "OPEN");
    createTestIncident("INC-002", "MEDIUM", "INVESTIGATING");
    createTestIncident("INC-003", "LOW", "RESOLVED");

    InvestigationCriteria criteria = InvestigationCriteria.builder().build();
    InvestigationResult<IncidentSummary> result =
        investigationService.findIncidents(criteria, 0, 10);

    assertNotNull(result);
    assertEquals(3, result.getTotalCount());
    assertEquals(3, result.getItemCount());
    assertFalse(result.hasMore());
  }

  @Test
  void testFindIncidentsWithTimeRangeFilter() throws Exception {
    Instant now = Instant.now();
    Instant yesterday = now.minus(1, ChronoUnit.DAYS);
    Instant tomorrow = now.plus(1, ChronoUnit.DAYS);

    // Create incidents at different times
    createTestIncidentAt("INC-001", "HIGH", "OPEN", yesterday);
    createTestIncidentAt("INC-002", "MEDIUM", "OPEN", now);
    createTestIncidentAt("INC-003", "LOW", "OPEN", tomorrow);

    InvestigationCriteria criteria =
        InvestigationCriteria.builder()
            .timeRange(yesterday.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS))
            .build();

    InvestigationResult<IncidentSummary> result =
        investigationService.findIncidents(criteria, 0, 10);

    assertEquals(2, result.getTotalCount());
    assertEquals(2, result.getItemCount());
  }

  @Test
  void testFindIncidentById() throws Exception {
    String incidentId = createTestIncident("INC-001", "HIGH", "OPEN");

    var result = investigationService.findIncidentById(incidentId);

    assertTrue(result.isPresent());
    assertEquals(incidentId, result.get().getIncidentId());
    assertEquals("HIGH", result.get().getSeverity());
    assertEquals("OPEN", result.get().getStatus());
  }

  @Test
  void testFindIncidentByIdNotFound() throws Exception {
    var result = investigationService.findIncidentById("NON-EXISTENT");

    assertTrue(result.isEmpty());
  }

  @Test
  void testFindEvidenceForIncident() throws Exception {
    String incidentId = createTestIncident("INC-001", "HIGH", "OPEN");
    createTestEvidence(incidentId, "EVD-001", "MassDeletionRule");
    createTestEvidence(incidentId, "EVD-002", "RapidModificationRule");

    InvestigationResult<EvidenceSummary> result =
        investigationService.findEvidenceForIncident(incidentId);

    assertNotNull(result);
    assertEquals(2, result.getTotalCount());
    assertEquals(2, result.getItemCount());
    assertFalse(result.hasMore());
  }

  @Test
  void testFindTimelineForIncident() throws Exception {
    String incidentId = createTestIncident("INC-001", "HIGH", "OPEN");
    createTestTimelineEvent(incidentId, "INCIDENT_CREATED", Instant.now(), 1);
    createTestTimelineEvent(incidentId, "INCIDENT_UPDATED", Instant.now(), 2);

    InvestigationResult<TimelineEventSummary> result =
        investigationService.findTimelineForIncident(incidentId);

    assertNotNull(result);
    assertEquals(2, result.getTotalCount());
    assertEquals(2, result.getItemCount());

    // Verify chronological ordering
    assertEquals("INCIDENT_CREATED", result.getItems().get(0).getEventType());
    assertEquals("INCIDENT_UPDATED", result.getItems().get(1).getEventType());
  }

  @Test
  void testFindRelatedIncidentsByCorrelation() throws Exception {
    String correlationId = "CORR-001";
    createTestIncidentWithCorrelation("INC-001", "HIGH", "OPEN", correlationId);
    createTestIncidentWithCorrelation("INC-002", "MEDIUM", "OPEN", correlationId);
    createTestIncidentWithCorrelation("INC-003", "LOW", "OPEN", "CORR-002");

    InvestigationResult<IncidentSummary> result =
        investigationService.findRelatedIncidents(correlationId);

    assertNotNull(result);
    assertEquals(2, result.getTotalCount());
    assertEquals(2, result.getItemCount());
  }

  @Test
  void testPaginationWorks() throws Exception {
    // Create 25 incidents
    for (int i = 0; i < 25; i++) {
      createTestIncident("INC-" + String.format("%03d", i), "HIGH", "OPEN");
    }

    InvestigationCriteria criteria = InvestigationCriteria.builder().build();

    // First page
    InvestigationResult<IncidentSummary> page1 =
        investigationService.findIncidents(criteria, 0, 10);
    assertEquals(10, page1.getItemCount());
    assertEquals(25, page1.getTotalCount());
    assertTrue(page1.hasMore());

    // Second page
    InvestigationResult<IncidentSummary> page2 =
        investigationService.findIncidents(criteria, 1, 10);
    assertEquals(10, page2.getItemCount());
    assertEquals(25, page2.getTotalCount());
    assertTrue(page2.hasMore());

    // Third page
    InvestigationResult<IncidentSummary> page3 =
        investigationService.findIncidents(criteria, 2, 10);
    assertEquals(5, page3.getItemCount());
    assertEquals(25, page3.getTotalCount());
    assertFalse(page3.hasMore());
  }

  @Test
  void testInvalidPaginationThrowsException() {
    InvestigationCriteria criteria = InvestigationCriteria.builder().build();

    assertThrows(
        InvestigationException.class,
        () -> {
          investigationService.findIncidents(criteria, -1, 10);
        });

    assertThrows(
        InvestigationException.class,
        () -> {
          investigationService.findIncidents(criteria, 0, 0);
        });

    assertThrows(
        InvestigationException.class,
        () -> {
          investigationService.findIncidents(criteria, 0, 2000);
        });
  }

  @Test
  void testMetricsTracking() throws Exception {
    createTestIncident("INC-001", "HIGH", "OPEN");

    InvestigationCriteria criteria = InvestigationCriteria.builder().build();
    investigationService.findIncidents(criteria, 0, 10);

    var metrics = investigationService.getMetrics();
    assertTrue(metrics.totalQueries() > 0);
    assertTrue(metrics.incidentQueriesExecuted() > 0);
    assertTrue(metrics.totalQueryTimeMs() >= 0);
  }

  // -------------------------------------------------------------------------
  // Helper methods
  // -------------------------------------------------------------------------

  private String createTestIncident(String incidentId, String severity, String status)
      throws Exception {
    return createTestIncidentAt(incidentId, severity, status, Instant.now());
  }

  private String createTestIncidentAt(
      String incidentId, String severity, String status, Instant createdAt) throws Exception {
    IncidentEntity incident =
        IncidentEntity.builder()
            .incidentId(incidentId)
            .severity(severity)
            .confidence("HIGH")
            .status(status)
            .title("Test Incident " + incidentId)
            .description("Test description")
            .createdAt(createdAt)
            .updatedAt(createdAt)
            .lastSeenAt(createdAt)
            .correlationId(null)
            .escalationLevel(1)
            .detectionCount(1)
            .metadata("{}")
            .build();
    incidentRepo.insert(incident);
    return incidentId;
  }

  private String createTestIncidentWithCorrelation(
      String incidentId, String severity, String status, String correlationId) throws Exception {
    IncidentEntity incident =
        IncidentEntity.builder()
            .incidentId(incidentId)
            .severity(severity)
            .confidence("HIGH")
            .status(status)
            .title("Test Incident " + incidentId)
            .description("Test description")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .lastSeenAt(Instant.now())
            .correlationId(correlationId)
            .escalationLevel(1)
            .detectionCount(1)
            .metadata("{}")
            .build();
    incidentRepo.insert(incident);
    return incidentId;
  }

  private void createTestEvidence(String incidentId, String evidenceId, String ruleName)
      throws Exception {
    IncidentEvidenceEntity evidence =
        IncidentEvidenceEntity.builder()
            .evidenceId(evidenceId)
            .incidentId(incidentId)
            .detectionId(UUID.randomUUID().toString())
            .ruleName(ruleName)
            .severity("HIGH")
            .confidence("HIGH")
            .filePath("C:\\test\\file.txt")
            .detectedAt(Instant.now())
            .correlationId(null)
            .metadata("{}")
            .createdAt(Instant.now())
            .build();
    evidenceRepo.insert(evidence);
  }

  private void createTestTimelineEvent(
      String incidentId, String eventType, Instant timestamp, long sequence) throws Exception {
    ForensicTimelineEntity timeline =
        ForensicTimelineEntity.builder()
            .timelineId(UUID.randomUUID().toString())
            .timestamp(timestamp)
            .sequenceNumber(sequence)
            .eventType(eventType)
            .incidentId(incidentId)
            .evidenceId(null)
            .detectionId(null)
            .severity("HIGH")
            .description("Test event")
            .correlationId(null)
            .metadata("{}")
            .createdAt(Instant.now())
            .build();
    timelineRepo.insert(timeline);
  }

  private AppConfig createTestConfig(Path dbPath) {
    AppConfig base = ConfigManager.resolve();
    return new AppConfig(
        base.appHome(),
        base.logsDir(),
        base.dataDir(),
        base.configDir(),
        dbPath,
        base.appName(),
        base.appVersion(),
        base.debugMode());
  }
}
