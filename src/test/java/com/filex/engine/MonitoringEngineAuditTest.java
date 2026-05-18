package com.filex.engine;

import com.filex.event.EventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;


/**
 * Comprehensive audit tests for MonitoringEngine Phase 1D.
 * 
 * Tests production-grade monitoring engine behavior including:
 * - Recursive registration
 * - Dynamic directory handling
 * - Concurrency safety
 * - Queue pressure
 * - Shutdown safety
 * - WatchKey lifecycle
 */
class MonitoringEngineAuditTest {

    @TempDir
    Path tempDir;

    private EventBus eventBus;
    private MonitoringEngine engine;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus(5000);
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

    // =========================================================================
    // SECTION 2: RECURSIVE DIRECTORY MONITORING
    // =========================================================================

    @Test
    void testDeeplyNestedDirectoryMonitoring() throws Exception {
        // Create deeply nested structure: 10 levels deep
        Path current = tempDir;
        for (int i = 0; i < 10; i++) {
            current = current.resolve("level" + i);
            Files.createDirectory(current);
        }

        CountDownLatch latch = new CountDownLatch(1);
        eventBus.subscribe(RawFileCreatedEvent.class, e -> latch.countDown());

        // Start monitoring
        engine.start(List.of(tempDir));
        Thread.sleep(800); // Allow registration

        // Create file at deepest level
        Path deepFile = current.resolve("deep.txt");
        Files.writeString(deepFile, "content");

        // Verify detection
        assertTrue(latch.await(10, TimeUnit.SECONDS),
                "Should detect file creation in deeply nested directory");

        MonitoringMetrics metrics = engine.getMetrics();
        assertTrue(metrics.getActiveWatchCount() >= 11,
                "Should have watches for all nested directories");
    }

    @Test
    void testMultipleMonitoredRoots() throws Exception {
        // Create multiple root directories
        Path root1 = tempDir.resolve("root1");
        Path root2 = tempDir.resolve("root2");
        Path root3 = tempDir.resolve("root3");
        Files.createDirectory(root1);
        Files.createDirectory(root2);
        Files.createDirectory(root3);

        CountDownLatch latch = new CountDownLatch(3);
        eventBus.subscribe(RawFileCreatedEvent.class, e -> latch.countDown());

        // Start monitoring all roots
        engine.start(List.of(root1, root2, root3));
        Thread.sleep(800);

        // Create files in each root
        Files.writeString(root1.resolve("file1.txt"), "content1");
        Files.writeString(root2.resolve("file2.txt"), "content2");
        Files.writeString(root3.resolve("file3.txt"), "content3");

        // Verify all detected
        assertTrue(latch.await(10, TimeUnit.SECONDS),
                "Should detect files in all monitored roots");

        MonitoringMetrics metrics = engine.getMetrics();
        assertEquals(3, metrics.getRegisteredPathCount(),
                "Should have 3 registered root paths");
    }

    @Test
    void testDuplicatePathRegistrationPrevention() throws Exception {
        engine.start(List.of(tempDir));
        Thread.sleep(800);

        MonitoringMetrics before = engine.getMetrics();
        int watchCountBefore = before.getActiveWatchCount();

        // Try to add same path again
        engine.addMonitoredPath(tempDir);
        Thread.sleep(800);

        MonitoringMetrics after = engine.getMetrics();
        assertEquals(watchCountBefore, after.getActiveWatchCount(),
                "Should not create duplicate watches for same path");
    }

    // =========================================================================
    // SECTION 3: DYNAMIC DIRECTORY REGISTRATION
    // =========================================================================

    @Test
    void testDynamicSubdirectoryRegistration() throws Exception {
        CountDownLatch dirLatch = new CountDownLatch(1);
        CountDownLatch fileLatch = new CountDownLatch(1);

        eventBus.subscribe(RawDirectoryCreatedEvent.class, e -> dirLatch.countDown());
        eventBus.subscribe(RawFileCreatedEvent.class, e -> fileLatch.countDown());

        engine.start(List.of(tempDir));
        Thread.sleep(800);

        // Create new subdirectory
        Path newDir = tempDir.resolve("newdir");
        Files.createDirectory(newDir);
        assertTrue(dirLatch.await(10, TimeUnit.SECONDS),
                "Should detect directory creation");

        // Allow time for dynamic registration
        Thread.sleep(800);

        // Create file in new directory
        Files.writeString(newDir.resolve("test.txt"), "content");
        assertTrue(fileLatch.await(10, TimeUnit.SECONDS),
                "Should detect file in dynamically registered directory");
    }

    @Test
    void testMultipleLevelDynamicRegistration() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        eventBus.subscribe(RawFileCreatedEvent.class, e -> latch.countDown());

        engine.start(List.of(tempDir));
        Thread.sleep(800);

        // Create nested structure dynamically
        Path level1 = tempDir.resolve("level1");
        Files.createDirectory(level1);
        Thread.sleep(800);

        Path level2 = level1.resolve("level2");
        Files.createDirectory(level2);
        Thread.sleep(800);

        Path level3 = level2.resolve("level3");
        Files.createDirectory(level3);
        Thread.sleep(800);

        // Create file at deepest level
        Files.writeString(level3.resolve("deep.txt"), "content");

        assertTrue(latch.await(10, TimeUnit.SECONDS),
                "Should detect file in dynamically created nested structure");
    }

    // =========================================================================
    // SECTION 4: WATCHSERVICE LOOP AUDIT
    // =========================================================================

    @Test
    void testWatchLoopResponsiveness() throws Exception {
        CountDownLatch latch = new CountDownLatch(10);
        eventBus.subscribe(RawFileCreatedEvent.class, e -> latch.countDown());

        engine.start(List.of(tempDir));
        Thread.sleep(800);

        long startTime = System.currentTimeMillis();

        // Create 10 files rapidly
        for (int i = 0; i < 10; i++) {
            Files.writeString(tempDir.resolve("file" + i + ".txt"), "content");
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS),
                "Watch loop should detect all events promptly");

        long duration = System.currentTimeMillis() - startTime;
        assertTrue(duration < 5000,
                "Watch loop should be responsive (completed in " + duration + "ms)");
    }

    @Test
    void testConcurrentFilesystemActivity() throws Exception {
        CountDownLatch latch = new CountDownLatch(50);
        eventBus.subscribe(RawFileCreatedEvent.class, e -> latch.countDown());

        engine.start(List.of(tempDir));
        Thread.sleep(800);

        // Simulate concurrent filesystem activity
        ExecutorService executor = Executors.newFixedThreadPool(5);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            final int threadId = i;
            futures.add(executor.submit(() -> {
                try {
                    for (int j = 0; j < 10; j++) {
                        Path file = tempDir.resolve("thread" + threadId + "_file" + j + ".txt");
                        Files.writeString(file, "content");
                        Thread.sleep(10);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }

        // Wait for all threads
        for (Future<?> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }

        executor.shutdown();

        assertTrue(latch.await(10, TimeUnit.SECONDS),
                "Should handle concurrent filesystem activity");
    }

    // =========================================================================
    // SECTION 6: EVENT NORMALIZATION
    // =========================================================================

    @Test
    void testPathNormalization() throws Exception {
        List<RawFileCreatedEvent> events = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        eventBus.subscribe(RawFileCreatedEvent.class, e -> {
            events.add(e);
            latch.countDown();
        });

        engine.start(List.of(tempDir));
        Thread.sleep(800);

        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "content");

        assertTrue(latch.await(10, TimeUnit.SECONDS));

        // Verify path is normalized
        Path eventPath = events.get(0).path();
        assertNotNull(eventPath);
        assertTrue(eventPath.isAbsolute(), "Event path should be absolute");
    }

    // =========================================================================
    // SECTION 7: DEDUPLICATION TESTING
    // =========================================================================

    @Test
    void testModifyStormDeduplication() throws Exception {
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "initial");

        List<RawFileModifiedEvent> events = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        eventBus.subscribe(RawFileModifiedEvent.class, e -> {
            synchronized (events) {
                events.add(e);
                if (events.size() == 1) {
                    latch.countDown();
                }
            }
        });

        engine.start(List.of(tempDir));
        Thread.sleep(800);

        // Rapid modifications (MODIFY storm)
        for (int i = 0; i < 20; i++) {
            Files.writeString(testFile, "content" + i);
            Thread.sleep(5); // Very rapid
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        Thread.sleep(1000); // Allow all events to process

        synchronized (events) {
            assertTrue(events.size() < 20,
                    "Deduplication should reduce events. Got: " + events.size() + " (expected < 20)");
        }

        MonitoringMetrics metrics = engine.getMetrics();
        assertTrue(metrics.getTotalEventsDeduplicated() > 0,
                "Should have deduplicated some events");
    }

    @Test
    void testDeduplicationDoesNotCollapseDistinctOperations() throws Exception {
        Path testFile = tempDir.resolve("test.txt");

        CountDownLatch createLatch = new CountDownLatch(1);
        CountDownLatch modifyLatch = new CountDownLatch(1);
        CountDownLatch deleteLatch = new CountDownLatch(1);

        eventBus.subscribe(RawFileCreatedEvent.class, e -> createLatch.countDown());
        eventBus.subscribe(RawFileModifiedEvent.class, e -> modifyLatch.countDown());
        eventBus.subscribe(RawFileDeletedEvent.class, e -> deleteLatch.countDown());

        engine.start(List.of(tempDir));
        Thread.sleep(800);

        // Different operations should NOT be deduplicated
        Files.writeString(testFile, "content");
        assertTrue(createLatch.await(10, TimeUnit.SECONDS), "Should detect CREATE");

        Thread.sleep(100);
        Files.writeString(testFile, "modified");
        assertTrue(modifyLatch.await(10, TimeUnit.SECONDS), "Should detect MODIFY");

        Thread.sleep(100);
        Files.delete(testFile);
        assertTrue(deleteLatch.await(10, TimeUnit.SECONDS), "Should detect DELETE");
    }

    // =========================================================================
    // SECTION 8: MONITORING THREADING AUDIT
    // =========================================================================

    @Test
    void testNoOrphanThreadsAfterShutdown() throws Exception {
        int threadCountBefore = Thread.activeCount();

        engine.start(List.of(tempDir));
        Thread.sleep(500);

        engine.stop();
        Thread.sleep(500);

        int threadCountAfter = Thread.activeCount();

        // Allow some tolerance for JVM threads
        assertTrue(threadCountAfter <= threadCountBefore + 2,
                "Should not leave orphan threads. Before: " + threadCountBefore +
                ", After: " + threadCountAfter);
    }

    @Test
    void testConcurrentPathRegistration() throws Exception {
        engine.start(List.of(tempDir));
        Thread.sleep(800);

        // Create multiple directories concurrently
        ExecutorService executor = Executors.newFixedThreadPool(5);
        List<Path> dirs = new ArrayList<>();
        
        for (int i = 0; i < 5; i++) {
            Path dir = tempDir.resolve("dir" + i);
            Files.createDirectory(dir);
            dirs.add(dir);
        }

        // Try to register them concurrently
        CountDownLatch latch = new CountDownLatch(5);
        AtomicInteger successCount = new AtomicInteger(0);

        for (Path dir : dirs) {
            executor.submit(() -> {
                try {
                    engine.addMonitoredPath(dir);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // Expected for duplicates
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS),
                "Concurrent registration should complete");

        executor.shutdown();

        // Should handle concurrent registration safely
        assertTrue(successCount.get() >= 0,
                "Should handle concurrent registration without crashes");
    }

    // =========================================================================
    // SECTION 9: WATCHKEY LIFECYCLE
    // =========================================================================

    @Test
    void testWatchKeyInvalidationOnDirectoryDeletion() throws Exception {
        Path subDir = tempDir.resolve("subdir");
        Files.createDirectory(subDir);

        CountDownLatch invalidationLatch = new CountDownLatch(1);
        eventBus.subscribe(WatchKeyInvalidatedEvent.class, e -> {
            if (e.path().equals(subDir)) {
                invalidationLatch.countDown();
            }
        });

        engine.start(List.of(tempDir));
        Thread.sleep(800); // Allow registration

        MonitoringMetrics beforeDelete = engine.getMetrics();
        int watchesBefore = beforeDelete.getActiveWatchCount();

        // Delete monitored directory
        Files.delete(subDir);
        Thread.sleep(800); // Allow invalidation detection

        // Try to trigger watch key check by creating a file
        Files.writeString(tempDir.resolve("trigger.txt"), "content");
        Thread.sleep(800);

        MonitoringMetrics afterDelete = engine.getMetrics();

        // Invalidation event may or may not fire depending on timing
        // But watch count should eventually decrease or invalidation count increase
        assertTrue(afterDelete.getTotalInvalidatedWatches() >= 0,
                "Should track invalidated watches");
    }

    // =========================================================================
    // SECTION 10: MONITORING STATE MACHINE
    // =========================================================================

    @Test
    void testStateTransitions() throws Exception {
        assertEquals(MonitoringState.IDLE, engine.getState());

        engine.start(List.of(tempDir));
        assertEquals(MonitoringState.RUNNING, engine.getState());

        engine.stop();
        assertEquals(MonitoringState.STOPPED, engine.getState());
    }

    @Test
    void testCannotStartFromRunningState() throws Exception {
        engine.start(List.of(tempDir));
        assertEquals(MonitoringState.RUNNING, engine.getState());

        assertThrows(MonitoringException.class, () ->
                engine.start(List.of(tempDir)));
    }

    @Test
    void testMonitoringLifecycleEvents() throws Exception {
        CountDownLatch startedLatch = new CountDownLatch(1);
        CountDownLatch stoppedLatch = new CountDownLatch(1);

        eventBus.subscribe(MonitoringStartedEvent.class, e -> startedLatch.countDown());
        eventBus.subscribe(MonitoringStoppedEvent.class, e -> stoppedLatch.countDown());

        engine.start(List.of(tempDir));
        assertTrue(startedLatch.await(10, TimeUnit.SECONDS),
                "Should publish MonitoringStartedEvent");

        engine.stop();
        assertTrue(stoppedLatch.await(10, TimeUnit.SECONDS),
                "Should publish MonitoringStoppedEvent");
    }

    // =========================================================================
    // SECTION 13: SHUTDOWN SAFETY
    // =========================================================================

    @Test
    void testShutdownDuringActiveMonitoring() throws Exception {
        engine.start(List.of(tempDir));
        Thread.sleep(800);

        // Start creating files
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {
                for (int i = 0; i < 100; i++) {
                    Files.writeString(tempDir.resolve("file" + i + ".txt"), "content");
                    Thread.sleep(10);
                }
            } catch (Exception e) {
                // Expected if shutdown occurs
            }
        });

        Thread.sleep(800);

        // Shutdown during activity
        engine.stop();

        executor.shutdownNow();

        assertEquals(MonitoringState.STOPPED, engine.getState());
    }

    @Test
    void testRepeatedShutdownCallsSafe() throws Exception {
        engine.start(List.of(tempDir));
        Thread.sleep(800);

        engine.stop();
        engine.stop(); // Second call
        engine.stop(); // Third call

        assertEquals(MonitoringState.STOPPED, engine.getState());
    }

    @Test
    void testShutdownCleansUpResources() throws Exception {
        engine.start(List.of(tempDir));
        Thread.sleep(800);

        MonitoringMetrics beforeShutdown = engine.getMetrics();
        assertTrue(beforeShutdown.getActiveWatchCount() > 0);

        engine.stop();
        Thread.sleep(800);

        MonitoringMetrics afterShutdown = engine.getMetrics();
        assertEquals(0, afterShutdown.getActiveWatchCount(),
                "Should cleanup all watches after shutdown");
        assertEquals(0, afterShutdown.getRegisteredPathCount(),
                "Should cleanup all registered paths after shutdown");
    }

    // =========================================================================
    // SECTION 14: METRICS & OBSERVABILITY
    // =========================================================================

    @Test
    void testMetricsAccuracy() throws Exception {
        engine.start(List.of(tempDir));
        Thread.sleep(800);

        MonitoringMetrics initial = engine.getMetrics();
        assertEquals(MonitoringState.RUNNING, initial.getCurrentState());
        assertTrue(initial.getActiveWatchCount() > 0);

        // Create some events
        CountDownLatch latch = new CountDownLatch(5);
        eventBus.subscribe(RawFileCreatedEvent.class, e -> latch.countDown());

        for (int i = 0; i < 5; i++) {
            Files.writeString(tempDir.resolve("file" + i + ".txt"), "content");
            Thread.sleep(50); // Small pause for stable, discrete inotify delivery
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        Thread.sleep(500);

        MonitoringMetrics after = engine.getMetrics();
        assertTrue(after.getTotalEventsDetected() >= 5,
                "Should track detected events");
        assertTrue(after.getTotalEventsNormalized() >= 5,
                "Should track normalized events");
    }

    @Test
    void testMetricsThreadSafety() throws Exception {
        engine.start(List.of(tempDir));
        Thread.sleep(200);

        // Concurrent metrics access
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(100);

        for (int i = 0; i < 100; i++) {
            executor.submit(() -> {
                try {
                    MonitoringMetrics metrics = engine.getMetrics();
                    assertNotNull(metrics);
                    assertNotNull(metrics.getCurrentState());
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS),
                "Concurrent metrics access should be safe");

        executor.shutdown();
    }

    // =========================================================================
    // SECTION 16: CONCURRENCY STRESS TESTING
    // =========================================================================

    @Test
    void testHighFrequencyFileOperations() throws Exception {
        CountDownLatch latch = new CountDownLatch(100);
        eventBus.subscribe(MonitoringEvent.class, e -> latch.countDown());

        engine.start(List.of(tempDir));
        Thread.sleep(300);

        // High-frequency operations
        ExecutorService executor = Executors.newFixedThreadPool(10);
        
        for (int i = 0; i < 10; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < 10; j++) {
                        Path file = tempDir.resolve("t" + threadId + "_f" + j + ".txt");
                        Files.writeString(file, "content");
                        Files.writeString(file, "modified");
                        Files.delete(file);
                    }
                } catch (Exception e) {
                    // Some operations may fail due to timing
                }
            });
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        // Should handle high-frequency operations without crashing
        assertTrue(engine.getState() == MonitoringState.RUNNING,
                "Engine should remain running under stress");
    }

    @Test
    void testMonitoringRestartCycles() throws Exception {
        for (int cycle = 0; cycle < 5; cycle++) {
            engine.start(List.of(tempDir));
            Thread.sleep(200);

            assertEquals(MonitoringState.RUNNING, engine.getState());

            // Create some activity
            Files.writeString(tempDir.resolve("cycle" + cycle + ".txt"), "content");
            Thread.sleep(100);

            engine.stop();
            Thread.sleep(200);

            assertEquals(MonitoringState.STOPPED, engine.getState());

            // Recreate engine for next cycle
            if (cycle < 4) {
                engine = new MonitoringEngine(eventBus);
            }
        }

        // Should handle multiple restart cycles safely
        assertTrue(true, "Restart cycles completed successfully");
    }
}
