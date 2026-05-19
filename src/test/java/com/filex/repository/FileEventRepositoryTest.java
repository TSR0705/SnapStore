package com.filex.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.filex.config.AppConfig;
import com.filex.config.ConfigManager;
import com.filex.database.DatabaseManager;
import com.filex.model.FileEventEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for FileEventRepository. */
class FileEventRepositoryTest {

  private DatabaseManager dbManager;
  private FileEventRepository repository;
  private Path testDbPath;

  @BeforeEach
  void setUp() throws Exception {
    // Create a unique test database for each test
    testDbPath = Files.createTempFile("filex-test-", ".db");

    AppConfig config = ConfigManager.resolve();
    // Use test database path
    AppConfig testConfig =
        new AppConfig(
            config.appHome(),
            config.logsDir(),
            config.dataDir(),
            config.configDir(),
            testDbPath,
            config.appName(),
            config.appVersion(),
            config.debugMode());

    dbManager = new DatabaseManager(testConfig);
    dbManager.initialize();
    repository = dbManager.fileEventRepository();
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
  void testInsertAndFindByEventId() throws Exception {
    // Given
    FileEventEntity event =
        FileEventEntity.builder()
            .eventId(UUID.randomUUID().toString())
            .timestamp(Instant.now())
            .eventType("FILE_CREATED")
            .filePath("C:\\test\\file.txt")
            .fileSize(1024L)
            .suspicious(false)
            .build();

    // When
    long id = repository.insert(event);

    // Then
    assertTrue(id > 0);

    Optional<FileEventEntity> found = repository.findByEventId(event.getEventId());
    assertTrue(found.isPresent());
    assertEquals(event.getEventId(), found.get().getEventId());
    assertEquals(event.getFilePath(), found.get().getFilePath());
  }

  @Test
  void testFindAllPaginated() throws Exception {
    // Given: Insert multiple events
    for (int i = 0; i < 5; i++) {
      FileEventEntity event =
          FileEventEntity.builder()
              .eventId(UUID.randomUUID().toString())
              .timestamp(Instant.now())
              .eventType("FILE_MODIFIED")
              .filePath("C:\\test\\file" + i + ".txt")
              .suspicious(false)
              .build();
      repository.insert(event);
    }

    // When
    Page<FileEventEntity> page = repository.findAll(PageRequest.of(0, 3));

    // Then
    assertNotNull(page);
    assertEquals(3, page.content().size());
    assertEquals(5, page.totalElements());
  }

  @Test
  void testCountSuspicious() throws Exception {
    // Given
    FileEventEntity suspicious =
        FileEventEntity.builder()
            .eventId(UUID.randomUUID().toString())
            .timestamp(Instant.now())
            .eventType("FILE_DELETED")
            .filePath("C:\\system\\critical.dll")
            .suspicious(true)
            .riskScore(0.95)
            .build();

    repository.insert(suspicious);

    // When
    long count = repository.countSuspicious();

    // Then
    assertEquals(1, count);
  }
}
