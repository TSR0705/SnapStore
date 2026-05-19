package com.filex.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for EventDeduplicator. */
class EventDeduplicatorTest {

  private EventDeduplicator deduplicator;

  @BeforeEach
  void setUp() {
    deduplicator = new EventDeduplicator(100); // 100ms window
  }

  @Test
  void testFirstEventShouldProcess() {
    Path path = Paths.get("test.txt");
    assertTrue(deduplicator.shouldProcess(path, "MODIFY"));
  }

  @Test
  void testDuplicateWithinWindowShouldNotProcess() throws InterruptedException {
    Path path = Paths.get("test.txt");

    // First event
    assertTrue(deduplicator.shouldProcess(path, "MODIFY"));

    // Immediate duplicate
    assertFalse(deduplicator.shouldProcess(path, "MODIFY"));

    // Another duplicate within window
    Thread.sleep(50);
    assertFalse(deduplicator.shouldProcess(path, "MODIFY"));
  }

  @Test
  void testDuplicateAfterWindowShouldProcess() throws InterruptedException {
    Path path = Paths.get("test.txt");

    // First event
    assertTrue(deduplicator.shouldProcess(path, "MODIFY"));

    // Wait for window to expire
    Thread.sleep(150);

    // Should process again
    assertTrue(deduplicator.shouldProcess(path, "MODIFY"));
  }

  @Test
  void testDifferentOperationsShouldProcess() {
    Path path = Paths.get("test.txt");

    assertTrue(deduplicator.shouldProcess(path, "CREATE"));
    assertTrue(deduplicator.shouldProcess(path, "MODIFY"));
    assertTrue(deduplicator.shouldProcess(path, "DELETE"));
  }

  @Test
  void testDifferentPathsShouldProcess() {
    assertTrue(deduplicator.shouldProcess(Paths.get("test1.txt"), "MODIFY"));
    assertTrue(deduplicator.shouldProcess(Paths.get("test2.txt"), "MODIFY"));
  }

  @Test
  void testDeduplicatedCountIncreases() {
    Path path = Paths.get("test.txt");

    deduplicator.shouldProcess(path, "MODIFY");
    assertEquals(0, deduplicator.getDeduplicatedCount());

    deduplicator.shouldProcess(path, "MODIFY"); // Duplicate
    assertEquals(1, deduplicator.getDeduplicatedCount());

    deduplicator.shouldProcess(path, "MODIFY"); // Another duplicate
    assertEquals(2, deduplicator.getDeduplicatedCount());
  }

  @Test
  void testClearResetsState() {
    Path path = Paths.get("test.txt");

    deduplicator.shouldProcess(path, "MODIFY");
    deduplicator.shouldProcess(path, "MODIFY"); // Duplicate

    deduplicator.clear();

    // After clear, should process again
    assertTrue(deduplicator.shouldProcess(path, "MODIFY"));
  }

  @Test
  void testNullPathThrowsException() {
    assertThrows(NullPointerException.class, () -> deduplicator.shouldProcess(null, "MODIFY"));
  }

  @Test
  void testNullOperationThrowsException() {
    assertThrows(
        NullPointerException.class, () -> deduplicator.shouldProcess(Paths.get("test.txt"), null));
  }
}
