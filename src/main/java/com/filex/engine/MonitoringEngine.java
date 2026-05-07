package com.filex.engine;

import com.filex.event.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Production-grade filesystem monitoring engine using Java NIO WatchService.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Recursive directory watching</li>
 *   <li>Dynamic subdirectory registration</li>
 *   <li>Event normalization and deduplication</li>
 *   <li>Lifecycle-safe startup/shutdown</li>
 *   <li>Publishing monitoring events to EventBus</li>
 * </ul>
 *
 * <p>Thread ownership:
 * <ul>
 *   <li>MonitoringEngine owns watch loop thread</li>
 *   <li>Watch loop remains lightweight (no DB writes, no heavy processing)</li>
 *   <li>Events published async to EventBus for downstream processing</li>
 * </ul>
 *
 * <p>The monitoring engine does NOT:
 * <ul>
 *   <li>Perform detection logic</li>
 *   <li>Write directly to database</li>
 *   <li>Touch UI directly</li>
 *   <li>Compute threat scores</li>
 * </ul>
 */
public final class MonitoringEngine {

    private static final Logger log = LoggerFactory.getLogger(MonitoringEngine.class);

    private static final long SHUTDOWN_TIMEOUT_SECONDS = 10;

    private final EventBus eventBus;
    private final EventDeduplicator deduplicator;

    private WatchService watchService;
    private ExecutorService watchExecutor;

    private final Map<WatchKey, Path> watchKeyToPath = new ConcurrentHashMap<>();
    private final Map<Path, WatchKey> pathToWatchKey = new ConcurrentHashMap<>();
    private final Set<Path> monitoredRoots = ConcurrentHashMap.newKeySet();

    private volatile MonitoringState state = MonitoringState.IDLE;

    // Metrics
    private final AtomicLong totalEventsDetected = new AtomicLong(0);
    private final AtomicLong totalEventsNormalized = new AtomicLong(0);
    private final AtomicLong totalOverflowEvents = new AtomicLong(0);
    private final AtomicLong totalInvalidatedWatches = new AtomicLong(0);

    public MonitoringEngine(EventBus eventBus) {
        this(eventBus, new EventDeduplicator());
    }

    public MonitoringEngine(EventBus eventBus, EventDeduplicator deduplicator) {
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus must not be null");
        this.deduplicator = Objects.requireNonNull(deduplicator, "deduplicator must not be null");
        log.info("MonitoringEngine created");
    }

    /**
     * Starts monitoring the specified paths recursively.
     *
     * @param paths the root paths to monitor
     * @throws MonitoringException if monitoring fails to start
     */
    public synchronized void start(List<Path> paths) throws MonitoringException {
        if (state != MonitoringState.IDLE && state != MonitoringState.STOPPED) {
            throw new MonitoringException("Cannot start monitoring in state: " + state);
        }

        log.info("Starting monitoring engine for {} path(s)", paths.size());
        state = MonitoringState.STARTING;

        try {
            // Initialize WatchService
            watchService = FileSystems.getDefault().newWatchService();

            // Register all paths recursively
            for (Path path : paths) {
                if (!Files.exists(path)) {
                    log.warn("Path does not exist, skipping: {}", path);
                    continue;
                }
                if (!Files.isDirectory(path)) {
                    log.warn("Path is not a directory, skipping: {}", path);
                    continue;
                }
                registerRecursive(path);
                monitoredRoots.add(path);
            }

            // Start watch loop thread
            watchExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "filex-watch-loop");
                t.setDaemon(false);
                return t;
            });
            watchExecutor.submit(this::watchLoop);

            state = MonitoringState.RUNNING;
            log.info("Monitoring engine started successfully. Watching {} directories",
                    watchKeyToPath.size());

            // Publish started event
            List<String> pathStrings = paths.stream()
                    .map(Path::toString)
                    .toList();
            eventBus.publish(new MonitoringStartedEvent(pathStrings));

        } catch (IOException e) {
            state = MonitoringState.FAILED;
            throw new MonitoringException("Failed to start monitoring", e);
        }
    }

    /**
     * Stops monitoring and cleans up resources.
     */
    public synchronized void stop() {
        if (state == MonitoringState.STOPPED || state == MonitoringState.IDLE) {
            log.debug("Monitoring already stopped");
            return;
        }

        log.info("Stopping monitoring engine...");
        state = MonitoringState.STOPPING;

        try {
            // Shutdown watch executor
            if (watchExecutor != null) {
                watchExecutor.shutdown();
                if (!watchExecutor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    log.warn("Watch executor did not terminate in time, forcing shutdown");
                    watchExecutor.shutdownNow();
                }
            }

            // Close WatchService (invalidates all watch keys)
            if (watchService != null) {
                watchService.close();
            }

            // Clear mappings
            watchKeyToPath.clear();
            pathToWatchKey.clear();
            monitoredRoots.clear();

            state = MonitoringState.STOPPED;
            log.info("Monitoring engine stopped successfully");

            // Publish stopped event
            eventBus.publish(new MonitoringStoppedEvent("Normal shutdown"));

        } catch (IOException | InterruptedException e) {
            log.error("Error during monitoring shutdown", e);
            state = MonitoringState.FAILED;
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Adds a new path to monitor dynamically.
     *
     * @param path the path to monitor
     * @throws MonitoringException if registration fails
     */
    public synchronized void addMonitoredPath(Path path) throws MonitoringException {
        if (state != MonitoringState.RUNNING) {
            throw new MonitoringException("Cannot add path while monitoring is not running");
        }

        if (!Files.exists(path)) {
            throw new MonitoringException("Path does not exist: " + path);
        }

        if (!Files.isDirectory(path)) {
            throw new MonitoringException("Path is not a directory: " + path);
        }

        if (monitoredRoots.contains(path)) {
            log.debug("Path already monitored: {}", path);
            return;
        }

        try {
            registerRecursive(path);
            monitoredRoots.add(path);
            log.info("Added monitored path: {}", path);
        } catch (IOException e) {
            throw new MonitoringException("Failed to register path: " + path, e);
        }
    }

    /**
     * Returns current monitoring state.
     */
    public MonitoringState getState() {
        return state;
    }

    /**
     * Returns a snapshot of monitoring metrics.
     */
    public MonitoringMetrics getMetrics() {
        return new MonitoringMetrics(
                watchKeyToPath.size(),
                monitoredRoots.size(),
                totalEventsDetected.get(),
                totalEventsNormalized.get(),
                deduplicator.getDeduplicatedCount(),
                totalOverflowEvents.get(),
                totalInvalidatedWatches.get(),
                state
        );
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Registers a directory and all its subdirectories recursively.
     */
    private void registerRecursive(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                registerDirectory(dir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                log.warn("Failed to visit directory: {}", file, exc);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Registers a single directory with the WatchService.
     */
    private void registerDirectory(Path dir) throws IOException {
        // Check if already registered
        if (pathToWatchKey.containsKey(dir)) {
            log.trace("Directory already registered: {}", dir);
            return;
        }

        WatchKey key = dir.register(
                watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE
        );

        watchKeyToPath.put(key, dir);
        pathToWatchKey.put(dir, key);

        log.debug("Registered watch for directory: {}", dir);
    }

    /**
     * Main watch loop - runs on dedicated thread.
     * Remains lightweight: only detects events and publishes them.
     */
    private void watchLoop() {
        log.info("Watch loop started");

        while (state == MonitoringState.RUNNING) {
            try {
                WatchKey key = watchService.poll(100, TimeUnit.MILLISECONDS);
                if (key == null) {
                    continue;
                }

                Path dir = watchKeyToPath.get(key);
                if (dir == null) {
                    log.warn("WatchKey not found in mapping, skipping");
                    key.reset();
                    continue;
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    processWatchEvent(dir, event);
                }

                // Reset key and check validity
                boolean valid = key.reset();
                if (!valid) {
                    handleInvalidatedKey(key, dir);
                }

            } catch (InterruptedException e) {
                log.debug("Watch loop interrupted");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Unexpected error in watch loop", e);
            }
        }

        log.info("Watch loop stopped");
    }

    /**
     * Processes a single WatchEvent from the WatchService.
     */
    private void processWatchEvent(Path dir, WatchEvent<?> event) {
        totalEventsDetected.incrementAndGet();

        WatchEvent.Kind<?> kind = event.kind();

        // Handle OVERFLOW
        if (kind == StandardWatchEventKinds.OVERFLOW) {
            totalOverflowEvents.incrementAndGet();
            log.warn("WatchService OVERFLOW detected for directory: {}", dir);
            eventBus.publish(new MonitoringOverflowEvent(dir));
            return;
        }

        // Extract path
        @SuppressWarnings("unchecked")
        WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
        Path filename = pathEvent.context();
        Path fullPath = dir.resolve(filename);

        // Normalize path
        try {
            fullPath = fullPath.toRealPath();
        } catch (IOException e) {
            // Path may not exist (e.g., DELETE event)
            fullPath = fullPath.toAbsolutePath().normalize();
        }

        // Determine operation
        String operation = kind.name();

        // Deduplication
        if (!deduplicator.shouldProcess(fullPath, operation)) {
            return; // Duplicate event, skip
        }

        totalEventsNormalized.incrementAndGet();

        // Publish normalized event
        publishNormalizedEvent(fullPath, kind);

        // Handle directory creation (dynamic registration)
        if (kind == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(fullPath)) {
            try {
                registerRecursive(fullPath);
                log.debug("Dynamically registered new directory: {}", fullPath);
            } catch (IOException e) {
                log.error("Failed to register new directory: {}", fullPath, e);
            }
        }
    }

    /**
     * Publishes a normalized monitoring event to the EventBus.
     */
    private void publishNormalizedEvent(Path path, WatchEvent.Kind<?> kind) {
        try {
            MonitoringEvent event;

            if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                if (Files.isDirectory(path)) {
                    event = new RawDirectoryCreatedEvent(path);
                } else {
                    event = new RawFileCreatedEvent(path);
                }
            } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                event = new RawFileModifiedEvent(path);
            } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                event = new RawFileDeletedEvent(path);
            } else {
                log.warn("Unknown event kind: {}", kind);
                return;
            }

            // Publish async to avoid blocking watch loop
            boolean queued = eventBus.tryPublishAsync(event);
            if (!queued) {
                log.warn("Failed to queue monitoring event (EventBus full): {}", event);
            }

        } catch (Exception e) {
            log.error("Failed to publish monitoring event for path: {}", path, e);
        }
    }

    /**
     * Handles an invalidated WatchKey.
     */
    private void handleInvalidatedKey(WatchKey key, Path dir) {
        totalInvalidatedWatches.incrementAndGet();
        log.warn("WatchKey invalidated for directory: {}", dir);

        // Remove from mappings
        watchKeyToPath.remove(key);
        pathToWatchKey.remove(dir);

        // Publish invalidation event
        eventBus.publish(new WatchKeyInvalidatedEvent(dir, "WatchKey no longer valid"));
    }
}
