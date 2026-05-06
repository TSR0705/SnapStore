package com.filex.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Synchronous, in-process event bus for decoupled component communication.
 *
 * <p>Design decisions:
 * <ul>
 *   <li>Synchronous dispatch — events are delivered on the publishing thread.
 *       Subscribers that need async behavior must schedule work themselves.</li>
 *   <li>Type-safe subscriptions — subscribers register against an exact
 *       {@link AppEvent} subtype.</li>
 *   <li>Thread-safe registration — {@link CopyOnWriteArrayList} allows
 *       concurrent subscribe/unsubscribe without locking during dispatch.</li>
 *   <li>Isolated subscriber failures — one failing subscriber does not
 *       prevent others from receiving the event.</li>
 * </ul>
 *
 * <p>This bus is intentionally simple for Phase 1A. Async dispatch,
 * dead-letter queues, and priority ordering are deferred to later phases.
 */
public final class EventBus {

    private static final Logger log = LoggerFactory.getLogger(EventBus.class);

    /**
     * Map from event type → list of raw consumers.
     * ConcurrentHashMap for safe concurrent access to the outer map.
     * CopyOnWriteArrayList for safe iteration during dispatch.
     */
    @SuppressWarnings("rawtypes")
    private final Map<Class<?>, CopyOnWriteArrayList<Consumer>> subscribers = new ConcurrentHashMap<>();

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
        log.debug("Subscribed {} to {}", handler.getClass().getSimpleName(), eventType.getSimpleName());
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
            list.remove(handler);
            log.debug("Unsubscribed {} from {}", handler.getClass().getSimpleName(), eventType.getSimpleName());
        }
    }

    /**
     * Publishes an event to all registered subscribers of its exact type.
     *
     * <p>Subscriber exceptions are caught and logged individually so that
     * one failing subscriber cannot block others.
     *
     * @param event the event to publish; must not be null
     */
    @SuppressWarnings("unchecked")
    public void publish(AppEvent event) {
        if (event == null) throw new IllegalArgumentException("event must not be null");

        log.debug("Publishing event: {}", event);

        CopyOnWriteArrayList<Consumer> handlers = subscribers.get(event.getClass());
        if (handlers == null || handlers.isEmpty()) {
            log.debug("No subscribers for event type: {}", event.getClass().getSimpleName());
            return;
        }

        for (Consumer handler : handlers) {
            try {
                handler.accept(event);
            } catch (Exception e) {
                log.error("Subscriber threw exception handling event [{}]: {}",
                        event.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
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
     * Clears all subscribers. Should only be called during shutdown or tests.
     */
    public void clearAll() {
        subscribers.clear();
        log.debug("EventBus cleared.");
    }
}
