package com.filex.event;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Stress tests for EventBus concurrency safety. */
class EventBusConcurrencyStressTest {

  private EventBus eventBus;

  @AfterEach
  void tearDown() {
    if (eventBus != null) {
      eventBus.shutdown();
    }
  }

  @Test
  void testConcurrentPublishers() throws Exception {
    // Given
    eventBus = new EventBus();
    int publisherCount = 10;
    int eventsPerPublisher = 100;
    AtomicInteger receivedCount = new AtomicInteger(0);

    eventBus.subscribe(ApplicationStartedEvent.class, e -> receivedCount.incrementAndGet());

    // When - multiple threads publishing concurrently
    ExecutorService publishers = Executors.newFixedThreadPool(publisherCount);
    CountDownLatch latch = new CountDownLatch(publisherCount);

    for (int i = 0; i < publisherCount; i++) {
      publishers.submit(
          () -> {
            try {
              for (int j = 0; j < eventsPerPublisher; j++) {
                eventBus.publish(new ApplicationStartedEvent());
              }
            } finally {
              latch.countDown();
            }
          });
    }

    // Then
    assertTrue(latch.await(10, TimeUnit.SECONDS), "All publishers should complete");
    publishers.shutdown();
    assertTrue(publishers.awaitTermination(5, TimeUnit.SECONDS));

    assertEquals(
        publisherCount * eventsPerPublisher, receivedCount.get(), "All events should be received");
  }

  @Test
  void testConcurrentAsyncPublishers() throws Exception {
    // Given
    eventBus = new EventBus(1000);
    int publisherCount = 5;
    int eventsPerPublisher = 200;
    CountDownLatch receivedLatch = new CountDownLatch(publisherCount * eventsPerPublisher);

    eventBus.subscribe(ApplicationStartedEvent.class, e -> receivedLatch.countDown());

    // When - multiple threads publishing async concurrently
    ExecutorService publishers = Executors.newFixedThreadPool(publisherCount);
    CountDownLatch publishLatch = new CountDownLatch(publisherCount);

    for (int i = 0; i < publisherCount; i++) {
      publishers.submit(
          () -> {
            try {
              for (int j = 0; j < eventsPerPublisher; j++) {
                eventBus.publishAsync(new ApplicationStartedEvent());
              }
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            } finally {
              publishLatch.countDown();
            }
          });
    }

    // Then
    assertTrue(publishLatch.await(10, TimeUnit.SECONDS), "All publishers should complete");
    assertTrue(receivedLatch.await(15, TimeUnit.SECONDS), "All events should be dispatched");

    publishers.shutdown();
    assertTrue(publishers.awaitTermination(5, TimeUnit.SECONDS));
  }

  @Test
  void testConcurrentSubscribeUnsubscribe() throws Exception {
    // Given
    eventBus = new EventBus();
    int threadCount = 10;
    int iterations = 50;

    // When - concurrent subscribe/unsubscribe
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch latch = new CountDownLatch(threadCount);
    List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

    for (int i = 0; i < threadCount; i++) {
      executor.submit(
          () -> {
            try {
              for (int j = 0; j < iterations; j++) {
                Consumer<ApplicationStartedEvent> handler = e -> {};
                eventBus.subscribe(ApplicationStartedEvent.class, handler);
                eventBus.publish(new ApplicationStartedEvent());
                eventBus.unsubscribe(ApplicationStartedEvent.class, handler);
              }
            } catch (Exception e) {
              exceptions.add(e);
            } finally {
              latch.countDown();
            }
          });
    }

    // Then
    assertTrue(latch.await(30, TimeUnit.SECONDS), "All threads should complete");
    executor.shutdown();
    assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

    assertTrue(exceptions.isEmpty(), "No exceptions should occur: " + exceptions);
  }

  @Test
  void testEventBurstHandling() throws Exception {
    // Given
    eventBus = new EventBus(5000);
    int burstSize = 1000;
    CountDownLatch receivedLatch = new CountDownLatch(burstSize);

    eventBus.subscribe(ApplicationStartedEvent.class, e -> receivedLatch.countDown());

    // When - rapid burst of async events
    for (int i = 0; i < burstSize; i++) {
      eventBus.publishAsync(new ApplicationStartedEvent());
    }

    // Then
    assertTrue(receivedLatch.await(10, TimeUnit.SECONDS), "All burst events should be processed");
  }

  @Test
  void testShutdownDuringActiveDispatch() throws Exception {
    // Given
    eventBus = new EventBus();
    CountDownLatch processingLatch = new CountDownLatch(1);
    CountDownLatch startedLatch = new CountDownLatch(1);

    eventBus.subscribe(
        ApplicationStartedEvent.class,
        e -> {
          try {
            startedLatch.countDown();
            processingLatch.await(); // Block to simulate slow processing
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
          }
        });

    // When - publish async and shutdown while processing
    eventBus.publishAsync(new ApplicationStartedEvent());
    assertTrue(startedLatch.await(2, TimeUnit.SECONDS), "Processing should start");

    // Shutdown while event is being processed
    CompletableFuture<Void> shutdownFuture = CompletableFuture.runAsync(() -> eventBus.shutdown());

    // Release the processing
    processingLatch.countDown();

    // Then - shutdown should complete
    shutdownFuture.get(15, TimeUnit.SECONDS);
  }

  @Test
  void testNoDeadlockUnderLoad() throws Exception {
    // Given
    eventBus = new EventBus(1000);
    int threadCount = 20;
    int operationsPerThread = 100;

    // When - mixed operations under load
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch latch = new CountDownLatch(threadCount);
    AtomicInteger successCount = new AtomicInteger(0);

    for (int i = 0; i < threadCount; i++) {
      final int threadId = i;
      executor.submit(
          () -> {
            try {
              for (int j = 0; j < operationsPerThread; j++) {
                if (threadId % 3 == 0) {
                  eventBus.publish(new ApplicationStartedEvent());
                } else if (threadId % 3 == 1) {
                  eventBus.publishAsync(new ApplicationStartedEvent());
                } else {
                  Consumer<ApplicationStartedEvent> handler = e -> successCount.incrementAndGet();
                  eventBus.subscribe(ApplicationStartedEvent.class, handler);
                  eventBus.unsubscribe(ApplicationStartedEvent.class, handler);
                }
              }
            } catch (Exception e) {
              // Ignore for this test
            } finally {
              latch.countDown();
            }
          });
    }

    // Then - should complete without deadlock
    assertTrue(latch.await(30, TimeUnit.SECONDS), "Should complete without deadlock");
    executor.shutdown();
    assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
  }

  @Test
  void testQueueSaturationRecovery() throws Exception {
    // Given - small queue
    eventBus = new EventBus(10);
    CountDownLatch processingLatch = new CountDownLatch(1);
    AtomicInteger processedCount = new AtomicInteger(0);

    // Slow subscriber to cause saturation
    eventBus.subscribe(
        ApplicationStartedEvent.class,
        e -> {
          try {
            processingLatch.await();
            processedCount.incrementAndGet();
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
          }
        });

    // When - saturate queue
    int attemptedEvents = 20;
    int acceptedEvents = 0;
    for (int i = 0; i < attemptedEvents; i++) {
      if (eventBus.tryPublishAsync(new ApplicationStartedEvent())) {
        acceptedEvents++;
      }
    }

    // Then - some events should be rejected
    assertTrue(
        acceptedEvents < attemptedEvents, "Some events should be rejected when queue is full");

    // Release processing and verify recovery
    processingLatch.countDown();
    Thread.sleep(1000); // Allow processing

    assertTrue(processedCount.get() > 0, "Events should be processed after recovery");
  }
}
