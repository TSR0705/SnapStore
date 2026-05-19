package com.filex.engine;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Time-window based event deduplicator for filesystem monitoring.
 *
 * <p>Prevents duplicate events (especially MODIFY storms) from flooding the event pipeline by
 * collapsing events for the same path and operation within a configurable time window.
 *
 * <p>Thread-safe with bounded memory usage.
 */
public final class EventDeduplicator {

  private static final Logger log = LoggerFactory.getLogger(EventDeduplicator.class);

  private static final long DEFAULT_DEDUP_WINDOW_MS = 500;
  private static final int MAX_CACHE_SIZE = 10000;

  private final long dedupWindowMs;
  private final Map<DedupKey, Long> lastSeenTimestamps = new ConcurrentHashMap<>();
  private final AtomicLong deduplicatedCount = new AtomicLong(0);

  public EventDeduplicator() {
    this(DEFAULT_DEDUP_WINDOW_MS);
  }

  public EventDeduplicator(long dedupWindowMs) {
    if (dedupWindowMs < 0) {
      throw new IllegalArgumentException("dedupWindowMs must be non-negative");
    }
    this.dedupWindowMs = dedupWindowMs;
    log.info("EventDeduplicator initialized with window: {}ms", dedupWindowMs);
  }

  /**
   * Checks if an event should be processed or deduplicated.
   *
   * @param path the filesystem path
   * @param operation the operation type (CREATE, MODIFY, DELETE)
   * @return true if event should be processed, false if it's a duplicate
   */
  public boolean shouldProcess(Path path, String operation) {
    Objects.requireNonNull(path, "path must not be null");
    Objects.requireNonNull(operation, "operation must not be null");

    DedupKey key = new DedupKey(path, operation);
    long now = System.currentTimeMillis();

    Long lastSeen = lastSeenTimestamps.get(key);

    if (lastSeen != null && (now - lastSeen) < dedupWindowMs) {
      // Duplicate within time window
      deduplicatedCount.incrementAndGet();
      log.trace("Deduplicated event: {} {}", operation, path);
      return false;
    }

    // Update timestamp
    lastSeenTimestamps.put(key, now);

    // Bounded cache cleanup
    if (lastSeenTimestamps.size() > MAX_CACHE_SIZE) {
      cleanupStaleEntries(now);
    }

    return true;
  }

  /** Returns the total number of deduplicated events. */
  public long getDeduplicatedCount() {
    return deduplicatedCount.get();
  }

  /** Clears the deduplication cache. Primarily for testing. */
  public void clear() {
    lastSeenTimestamps.clear();
    log.debug("Deduplication cache cleared");
  }

  /** Removes stale entries from the cache to prevent unbounded growth. */
  private void cleanupStaleEntries(long now) {
    int removed = 0;
    for (Map.Entry<DedupKey, Long> entry : lastSeenTimestamps.entrySet()) {
      if (now - entry.getValue() > dedupWindowMs * 2) {
        lastSeenTimestamps.remove(entry.getKey());
        removed++;
      }
    }
    if (removed > 0) {
      log.debug("Cleaned up {} stale deduplication entries", removed);
    }
  }

  /** Composite key for deduplication: path + operation. */
  private static final class DedupKey {
    private final Path path;
    private final String operation;

    DedupKey(Path path, String operation) {
      this.path = path;
      this.operation = operation;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      DedupKey dedupKey = (DedupKey) o;
      return Objects.equals(path, dedupKey.path) && Objects.equals(operation, dedupKey.operation);
    }

    @Override
    public int hashCode() {
      return Objects.hash(path, operation);
    }
  }
}
