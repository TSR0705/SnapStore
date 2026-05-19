package com.filex.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Production-grade event bus for decoupled component communication.
 *
 * <p>Features:
 * <ul>
 *   <li>Synchronous dispatch — events delivered on publishing thread</li>
 *   <li>Asynchronous dispatch — events queued and dispatched on background threads</li>
 *   <li>Type-safe subscriptions — subscribers register against exact event types</li>
 *   <li>Thread-safe registration — concurrent subscribe/unsubscribe supported</li>
 *   <li>Isolated subscriber failures — one failing subscriber doesn't affect others</li>
 *   <li>Bounded queue — prevents memory exhaustion during event storms</li>
 *   <li>Lifecycle-safe shutdown — drains queue and stops executors cleanly</li>
 * </ul>
 *
 * <p>Thread ownership:
 * <ul>
 *   <li>EventBus owns dispatch executors</li>
 *   <li>Async events dispatched on "filex-event-dispatcher" threads</li>
 *   <li>Sync events dispatched on caller thread</li>
 * </ul>
 */
public final class EventBus {

    private static final Logger log = LoggerFactory.getLogger(EventBus.class);

    private static final int DEFAULT_QUEUE_CAPACITY = 10000;
    private static final int DISPATCHER_THREADS = 2;
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 10;

    /**
     * Map from event type → list of raw consumers.
     * ConcurrentHashMap for safe concurrent access to the outer map.
     * CopyOnWriteArrayList for safe iteration during dispatch.
     */
    @SuppressWarnings("rawtypes")
    private final Map<Class<?>, CopyOnWriteArrayList<Consumer>> subscribers = new ConcurrentHashMap<>();

    /**
     * Bounded queue for async event dispatch.
     * Prevents memory exhaustion during event storms.
     */
    private final BlockingQueue<AppEvent> asyncQueue;

    /**
     * Executor for async event dispatch.
     * Owned by EventBus, shutdown during cleanup.
     */
    private final ExecutorService dispatchExecutor;

    /**
     * Shutdown flag to stop accepting new async events.
     */
    private volatile boolean shutdown = false;

    /**
     * Metrics counters for observability.
     */
    private final AtomicLong totalPublishedEvents = new AtomicLong(0);
    private final AtomicLong totalAsyncEvents = new AtomicLong(0);
    private final AtomicLong totalDroppedEvents = new AtomicLong(0);
    private final AtomicLong totalDispatchFailures = new AtomicLong(0);
    private final int queueCapacity;

    public EventBus() {
        this(DEFAULT_QUEUE_CAPACITY);
    }

    public EventBus(int queueCapacity) {
        this.queueCapacity = queueCapacity;
        this.asyncQueue = new LinkedBlockingQueue<>(queueCapacity);
        this.dispatchExecutor = Executors.newFixedThreadPool(
                DISPATCHER_THREADS,
                new ThreadFactory() {
                    private final AtomicInteger counter = new AtomicInteger(0);
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "filex-event-dispatcher-" + counter.incrementAndGet());
                        t.setDaemon(false); // Ensure proper shutdown
                        return t;
                    }
                }
        );

        // Start dispatcher workers
        for (int i = 0; i < DISPATCHER_THREADS; i++) {
            dispatchExecutor.submit(this::dispatchLoop);
        }

        log.info("EventBus initialized with queue capacity: {}, dispatcher threads: {}",
                queueCapacity, DISPATCHER_THREADS);
    }

    /**
     * Registers a subscriber for events of the given type.
     *
     * @param eventType the exact event class to subscribe to
     * @param handler   the consumer to invoke when an event is published
     * @param <T>       event type bound
     */
    public <T extends AppEvent> void subscribe(Class<T> eventType, Consumer<T> handler) {
        if (eventType == null) throw new IllegalArgumentException("eventType must not be null");
        if (handler == null)   throw new IllegalArgumentException("handler must not be null");

        subscribers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(handler);
        
        int subscriberCount = subscribers.get(eventType).size();
        log.debug("Subscribed {} to {}", handler.getClass().getSimpleName(), eventType.getSimpleName());
        log.debug(com.filex.validation.TruthMarkers.TRUTH,
                "component=EventBus event=subscriber_registered eventType={} handlerClass={} totalSubscribers={}",
                sanitize(eventType.getSimpleName()), sanitize(handler.getClass().getSimpleName()),
                subscriberCount);
    }

    /**
     * Removes a previously registered subscriber.
     *
     * @param eventType the event class the handler was registered for
     * @param handler   the exact handler instance to remove
     * @param <T>       event type bound
     */
    public <T extends AppEvent> void unsubscribe(Class<T> eventType, Consumer<T> handler) {
        if (eventType == null || handler == null) return;

        CopyOnWriteArrayList<?> list = subscribers.get(eventType);
        if (list != null) {
            boolean removed = list.remove(handler);
            if (removed) {
                log.debug("Unsubscribed {} from {}", handler.getClass().getSimpleName(), eventType.getSimpleName());
            }
        }
    }

    /**
     * Publishes an event synchronously to all registered subscribers.
     *
     * <p>Event is dispatched immediately on the calling thread.
     * Subscriber exceptions are caught and logged individually.
     *
     * @param event the event to publish; must not be null
     */
    public void publish(AppEvent event) {
        if (event == null) throw new IllegalArgumentException("event must not be null");

        totalPublishedEvents.incrementAndGet();
        
        int subscriberCount = subscriberCount(event.getClass());
        log.debug("Publishing event (sync): {}", event);
        log.trace(com.filex.validation.TruthMarkers.TRUTH,
                "component=EventBus event=event_published mode=sync eventType={} eventId={} subscriberCount={}",
                sanitize(event.getClass().getSimpleName()), sanitize(event.eventId()), subscriberCount);
        
        dispatchToSubscribers(event);
        log.info(com.filex.validation.TruthMarkers.TRUTH,
                "TRUTH stage=EventBus action=publish_sync eventType={} eventId={} result=success",
                sanitize(event.getClass().getSimpleName()), sanitize(event.eventId()));
    }

    /**
     * Publishes an event asynchronously via the dispatch queue.
     *
     * <p>Event is queued and dispatched on a background thread.
     * If the queue is full, this method blocks until space is available
     * or throws if shutdown.
     *
     * @param event the event to publish; must not be null
     * @throws IllegalStateException if EventBus is shutdown
     * @throws InterruptedException if interrupted while waiting for queue space
     */
    public void publishAsync(AppEvent event) throws InterruptedException {
        if (event == null) throw new IllegalArgumentException("event must not be null");
        if (shutdown) throw new IllegalStateException("EventBus is shutdown");

        totalPublishedEvents.incrementAndGet();
        totalAsyncEvents.incrementAndGet();
        log.debug("Publishing event (async): {}", event);
        asyncQueue.put(event); // Blocks if queue is full
        log.info(com.filex.validation.TruthMarkers.TRUTH,
                "TRUTH stage=EventBus action=publish_async eventType={} eventId={} result=success",
                sanitize(event.getClass().getSimpleName()), sanitize(event.eventId()));
    }

    /**
     * Attempts to publish an event asynchronously without blocking.
     *
     * @param event the event to publish
     * @return true if event was queued, false if queue is full
     * @throws IllegalStateException if EventBus is shutdown
     */
    public boolean tryPublishAsync(AppEvent event) {
        if (event == null) throw new IllegalArgumentException("event must not be null");
        if (shutdown) throw new IllegalStateException("EventBus is shutdown");

        boolean queued = asyncQueue.offer(event);
        if (queued) {
            totalPublishedEvents.incrementAndGet();
            totalAsyncEvents.incrementAndGet();
            int subscriberCount = subscriberCount(event.getClass());
            log.debug("Publishing event (async): {}", event);
            log.info(com.filex.validation.TruthMarkers.TRUTH,
                    "TRUTH stage=EventBus action=publish_async eventType={} eventId={} result=success",
                    sanitize(event.getClass().getSimpleName()), sanitize(event.eventId()));
            log.trace(com.filex.validation.TruthMarkers.TRUTH,
                    "component=EventBus event=event_published mode=async eventType={} eventId={} subscriberCount={} queued=true",
                    sanitize(event.getClass().getSimpleName()), sanitize(event.eventId()), subscriberCount);
        } else {
            totalDroppedEvents.incrementAndGet();
            log.warn("Async queue full, event dropped: {}", event.getClass().getSimpleName());
            log.info(com.filex.validation.TruthMarkers.TRUTH,
                    "TRUTH stage=EventBus action=publish_async eventType={} eventId={} result=failure reason=queue_full",
                    sanitize(event.getClass().getSimpleName()), sanitize(event.eventId()));
            log.warn(com.filex.validation.TruthMarkers.TRUTH,
                    "component=EventBus event=event_dropped mode=async eventType={} eventId={} reason=queue_full",
                    sanitize(event.getClass().getSimpleName()), sanitize(event.eventId()));
        }
        return queued;
    }

    /**
     * Returns the number of subscribers registered for a given event type.
     * Primarily useful for testing.
     */
    public int subscriberCount(Class<? extends AppEvent> eventType) {
        CopyOnWriteArrayList<?> list = subscribers.get(eventType);
        return list == null ? 0 : list.size();
    }

    /**
     * Returns the current size of the async dispatch queue.
     */
    public int queueSize() {
        return asyncQueue.size();
    }

    /**
     * Returns a snapshot of EventBus runtime metrics.
     *
     * <p>Provides observability into queue depth, subscriber count,
     * event counters, and dispatch failures for monitoring and diagnostics.
     */
    public EventBusMetrics getMetrics() {
        int totalSubscribers = subscribers.values().stream()
                .mapToInt(CopyOnWriteArrayList::size)
                .sum();

        return new EventBusMetrics(
                asyncQueue.size(),
                queueCapacity,
                totalSubscribers,
                DISPATCHER_THREADS,
                totalPublishedEvents.get(),
                totalAsyncEvents.get(),
                totalDroppedEvents.get(),
                totalDispatchFailures.get(),
                shutdown
        );
    }

    /**
     * Shuts down the EventBus cleanly.
     *
     * <p>Shutdown sequence:
     * <ol>
     *   <li>Stop accepting new async events</li>
     *   <li>Drain remaining events from queue</li>
     *   <li>Shutdown dispatch executor</li>
     *   <li>Clear all subscribers</li>
     * </ol>
     */
    public void shutdown() {
        if (shutdown) {
            log.warn("EventBus already shutdown");
            return;
        }

        log.info("EventBus shutting down...");
        shutdown = true;

        // Shutdown executor (will finish current tasks)
        dispatchExecutor.shutdown();

        try {
            if (!dispatchExecutor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("EventBus dispatch executor did not terminate in time, forcing shutdown");
                dispatchExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.warn("Interrupted while waiting for EventBus shutdown");
            dispatchExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Clear subscribers
        subscribers.clear();

        log.info("EventBus shutdown complete. Remaining queue size: {}", asyncQueue.size());
    }

    /**
     * Clears all subscribers. Should only be called during tests.
     */
    public void clearAll() {
        subscribers.clear();
        log.debug("EventBus subscribers cleared.");
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Dispatch loop for async event processing.
     * Runs on dispatcher threads.
     */
    private void dispatchLoop() {
        log.debug("Event dispatcher thread started: {}", Thread.currentThread().getName());

        while (!shutdown || !asyncQueue.isEmpty()) {
            try {
                AppEvent event = asyncQueue.poll(100, TimeUnit.MILLISECONDS);
                if (event != null) {
                    dispatchToSubscribers(event);
                }
            } catch (InterruptedException e) {
                log.debug("Event dispatcher interrupted");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Unexpected error in event dispatcher", e);
            }
        }

        log.debug("Event dispatcher thread stopped: {}", Thread.currentThread().getName());
    }

    /**
     * Dispatches an event to all registered subscribers.
     * Isolates subscriber failures.
     */
    @SuppressWarnings("unchecked")
    private void dispatchToSubscribers(AppEvent event) {
        CopyOnWriteArrayList<Consumer> handlers = subscribers.get(event.getClass());
        if (handlers == null || handlers.isEmpty()) {
            log.trace("No subscribers for event type: {}", event.getClass().getSimpleName());
            log.trace(com.filex.validation.TruthMarkers.TRUTH,
                    "component=EventBus event=no_subscribers eventType={} eventId={}",
                    sanitize(event.getClass().getSimpleName()), sanitize(event.eventId()));
            return;
        }

        log.trace("Dispatching event to {} subscriber(s): {}", handlers.size(), event.getClass().getSimpleName());
        log.trace(com.filex.validation.TruthMarkers.TRUTH,
                "component=EventBus event=dispatch_started eventType={} eventId={} subscriberCount={}",
                sanitize(event.getClass().getSimpleName()), sanitize(event.eventId()), handlers.size());

        int successCount = 0;
        int failureCount = 0;
        
        // Propagate MDC correlation identifier across thread boundary
        String eventCorrelationId = event.correlationId();
        String oldMdcId = org.slf4j.MDC.get(com.filex.validation.TruthValidationCoordinator.MDC_VALIDATION_RUN_ID);
        try {
            if (eventCorrelationId != null) {
                org.slf4j.MDC.put(com.filex.validation.TruthValidationCoordinator.MDC_VALIDATION_RUN_ID, eventCorrelationId);
            } else if (oldMdcId != null) {
                org.slf4j.MDC.put(com.filex.validation.TruthValidationCoordinator.MDC_VALIDATION_RUN_ID, oldMdcId);
            }

            for (Consumer handler : handlers) {
                try {
                    handler.accept(event);
                    successCount++;
                    log.info(com.filex.validation.TruthMarkers.TRUTH,
                            "TRUTH stage=EventBus action=dispatch_subscriber eventType={} eventId={} subscriber={} result=success",
                            sanitize(event.getClass().getSimpleName()), sanitize(event.eventId()),
                            sanitize(handler.getClass().getSimpleName()));
                    log.trace(com.filex.validation.TruthMarkers.TRUTH,
                            "component=EventBus event=subscriber_dispatched eventType={} eventId={} subscriber={} outcome=success",
                            sanitize(event.getClass().getSimpleName()), sanitize(event.eventId()),
                            sanitize(handler.getClass().getSimpleName()));
                } catch (Exception e) {
                    failureCount++;
                    totalDispatchFailures.incrementAndGet();
                    log.error("Subscriber threw exception handling event [{}]: {}",
                            event.getClass().getSimpleName(), e.getMessage(), e);
                    log.info(com.filex.validation.TruthMarkers.TRUTH,
                            "TRUTH stage=EventBus action=dispatch_subscriber eventType={} eventId={} subscriber={} result=failure err={}",
                            sanitize(event.getClass().getSimpleName()), sanitize(event.eventId()),
                            sanitize(handler.getClass().getSimpleName()), sanitize(e.getMessage()));
                    log.error(com.filex.validation.TruthMarkers.TRUTH,
                            "component=EventBus event=subscriber_dispatched eventType={} eventId={} subscriber={} outcome=failure err={}",
                            sanitize(event.getClass().getSimpleName()), sanitize(event.eventId()),
                            sanitize(handler.getClass().getSimpleName()), sanitize(e.getMessage()));
                }
            }
        } finally {
            if (oldMdcId != null) {
                org.slf4j.MDC.put(com.filex.validation.TruthValidationCoordinator.MDC_VALIDATION_RUN_ID, oldMdcId);
            } else {
                org.slf4j.MDC.remove(com.filex.validation.TruthValidationCoordinator.MDC_VALIDATION_RUN_ID);
            }
        }
        
        log.trace(com.filex.validation.TruthMarkers.TRUTH,
                "component=EventBus event=dispatch_completed eventType={} eventId={} successCount={} failureCount={}",
                sanitize(event.getClass().getSimpleName()), sanitize(event.eventId()), successCount, failureCount);
    }

    /**
     * Sanitizes a value for structured logging.
     */
    private static String sanitize(Object value) {
        if (value == null) {
            return "null";
        }
        String s = value.toString();
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c) || Character.isISOControl(c)) {
                sb.append('_');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
