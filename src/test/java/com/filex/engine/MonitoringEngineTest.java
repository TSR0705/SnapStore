package com.filex.engine;

import static org.junit.jupiter.api.Assertions.*;

import com.filex.event.EventBus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for MonitoringEngine. */
class MonitoringEngineTest {

  @TempDir Path tempDir;

  private EventBus eventBus;
  private MonitoringEngine engine;

  @BeforeEach
  void setUp() {
    eventBus = new EventBus(1000);
    engine = new MonitoringEngine(eventBus);
  }

  @AfterEach
  void tearDown() {
    if (engine != null && engine.getState() == MonitoringState.RUNNING) {
      engine.stop();
    }
    if (eventBus != null) {
      eventBus.shutdown();
    }
  }

  @Test
  void testInitialState() {
    assertEquals(MonitoringState.IDLE, engine.getState());
  }

  @Test
  void testStartMonitoring() throws Exception {
    // Given
    CountDownLatch latch = new CountDownLatch(1);
    eventBus.subscribe(MonitoringStartedEvent.class, e -> latch.countDown());

    // When
    engine.start(List.of(tempDir));

    // Then
    assertTrue(latch.await(2, TimeUnit.SECONDS), "MonitoringStartedEvent should be published");
    assertEquals(MonitoringState.RUNNING, engine.getState());
  }

  @Test
  void testStopMonitoring() throws Exception {
    // Given
    engine.start(List.of(tempDir));
    assertEquals(MonitoringState.RUNNING, engine.getState());

    CountDownLatch latch = new CountDownLatch(1);
    eventBus.subscribe(MonitoringStoppedEvent.class, e -> latch.countDown());

    // When
    engine.stop();

    // Then
    assertTrue(latch.await(2, TimeUnit.SECONDS), "MonitoringStoppedEvent should be published");
    assertEquals(MonitoringState.STOPPED, engine.getState());
  }

  @Test
  void testDetectFileCreation() throws Exception {
    // Given
    CountDownLatch latch = new CountDownLatch(1);
    List<RawFileCreatedEvent> events = new ArrayList<>();

    eventBus.subscribe(
        RawFileCreatedEvent.class,
        e -> {
          events.add(e);
          latch.countDown();
        });

    engine.start(List.of(tempDir));
    Thread.sleep(200); // Let monitoring stabilize

    // When
    Path testFile = tempDir.resolve("test.txt");
    Files.writeString(testFile, "test content");

    // Then
    assertTrue(latch.await(3, TimeUnit.SECONDS), "Should detect file creation");
    assertEquals(1, events.size());
    assertTrue(events.get(0).path().toString().contains("test.txt"));
  }

  @Test
  void testDetectFileModification() throws Exception {
    // Given
    Path testFile = tempDir.resolve("test.txt");
    Files.writeString(testFile, "initial content");

    CountDownLatch latch = new CountDownLatch(1);
    List<RawFileModifiedEvent> events = new ArrayList<>();

    eventBus.subscribe(
        RawFileModifiedEvent.class,
        e -> {
          events.add(e);
          latch.countDown();
        });

    engine.start(List.of(tempDir));
    Thread.sleep(200); // Let monitoring stabilize

    // When
    Files.writeString(testFile, "modified content");

    // Then
    assertTrue(latch.await(3, TimeUnit.SECONDS), "Should detect file modification");
    assertFalse(events.isEmpty());
  }

  @Test
  void testDetectFileDeletion() throws Exception {
    // Given
    Path testFile = tempDir.resolve("test.txt");
    Files.writeString(testFile, "content");

    CountDownLatch latch = new CountDownLatch(1);
    List<RawFileDeletedEvent> events = new ArrayList<>();

    eventBus.subscribe(
        RawFileDeletedEvent.class,
        e -> {
          events.add(e);
          latch.countDown();
        });

    engine.start(List.of(tempDir));
    Thread.sleep(200); // Let monitoring stabilize

    // When
    Files.delete(testFile);

    // Then
    assertTrue(latch.await(3, TimeUnit.SECONDS), "Should detect file deletion");
    assertEquals(1, events.size());
  }

  @Test
  void testDynamicDirectoryRegistration() throws Exception {
    // Given
    CountDownLatch latch = new CountDownLatch(2); // Directory + file creation
    List<MonitoringEvent> events = new ArrayList<>();

    eventBus.subscribe(
        RawDirectoryCreatedEvent.class,
        e -> {
          events.add(e);
          latch.countDown();
        });

    eventBus.subscribe(
        RawFileCreatedEvent.class,
        e -> {
          events.add(e);
          latch.countDown();
        });

    engine.start(List.of(tempDir));
    Thread.sleep(200);

    // When - create subdirectory
    Path subDir = tempDir.resolve("subdir");
    Files.createDirectory(subDir);
    Thread.sleep(300); // Allow time for dynamic registration

    // Create file in new subdirectory
    Path fileInSubDir = subDir.resolve("test.txt");
    Files.writeString(fileInSubDir, "content");

    // Then
    assertTrue(latch.await(5, TimeUnit.SECONDS), "Should detect both directory and file creation");
    assertTrue(events.size() >= 2);
  }

  @Test
  void testMetrics() throws Exception {
    // Given
    engine.start(List.of(tempDir));
    Thread.sleep(200);

    // When
    Path testFile = tempDir.resolve("test.txt");
    Files.writeString(testFile, "content");
    Thread.sleep(500); // Allow event processing

    MonitoringMetrics metrics = engine.getMetrics();

    // Then
    assertNotNull(metrics);
    assertEquals(MonitoringState.RUNNING, metrics.getCurrentState());
    assertTrue(metrics.getActiveWatchCount() > 0, "Should have active watches");
    assertTrue(metrics.getTotalEventsDetected() > 0, "Should have detected events");
  }

  @Test
  void testCannotStartWhenRunning() throws Exception {
    // Given
    engine.start(List.of(tempDir));

    // When/Then
    assertThrows(MonitoringException.class, () -> engine.start(List.of(tempDir)));
  }

  @Test
  void testAddMonitoredPathWhileRunning() throws Exception {
    // Given
    engine.start(List.of(tempDir));
    Path newDir = tempDir.resolve("newdir");
    Files.createDirectory(newDir);

    // When
    engine.addMonitoredPath(newDir);

    // Then
    MonitoringMetrics metrics = engine.getMetrics();
    assertTrue(metrics.getRegisteredPathCount() >= 1);
  }

  @Test
  void testAddNonExistentPathThrowsException() throws Exception {
    // Given
    engine.start(List.of(tempDir));
    Path nonExistent = tempDir.resolve("does-not-exist");

    // When/Then
    assertThrows(MonitoringException.class, () -> engine.addMonitoredPath(nonExistent));
  }

  @Test
  void testMultipleStopCallsSafe() throws Exception {
    // Given
    engine.start(List.of(tempDir));

    // When
    engine.stop();
    engine.stop(); // Second call should be safe

    // Then
    assertEquals(MonitoringState.STOPPED, engine.getState());
  }

  @Test
  void testDeduplicationPreventsModifyStorm() throws Exception {
    // Given
    Path testFile = tempDir.resolve("test.txt");
    Files.writeString(testFile, "initial");

    CountDownLatch latch = new CountDownLatch(1);
    List<RawFileModifiedEvent> events = new ArrayList<>();

    eventBus.subscribe(
        RawFileModifiedEvent.class,
        e -> {
          events.add(e);
          if (events.size() == 1) {
            latch.countDown();
          }
        });

    engine.start(List.of(tempDir));
    Thread.sleep(200);

    // When - rapid modifications
    for (int i = 0; i < 10; i++) {
      Files.writeString(testFile, "content" + i);
      Thread.sleep(10); // Very rapid
    }

    // Then
    assertTrue(latch.await(3, TimeUnit.SECONDS));
    Thread.sleep(1000); // Allow all events to process

    // Should have significantly fewer than 10 events due to deduplication
    assertTrue(
        events.size() < 10, "Deduplication should reduce event count. Got: " + events.size());
  }
}
