package com.filex.investigation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable result of an investigation query.
 *
 * <p>Contains incidents, evidence, and timeline events matching the investigation
 * criteria, along with metadata about the query execution.
 *
 * <p>Thread-safety: Immutable after construction.
 *
 * @param <T> the type of result items (IncidentSummary, EvidenceSummary, or TimelineEventSummary)
 */
public final class InvestigationResult<T> {

    private final List<T> items;
    private final long totalCount;
    private final int pageNumber;
    private final int pageSize;
    private final Instant queryTimestamp;
    private final long queryDurationMs;
    private final boolean hasMore;

    private InvestigationResult(Builder<T> builder) {
        this.items = List.copyOf(Objects.requireNonNull(builder.items, "items must not be null"));
        this.totalCount = builder.totalCount;
        this.pageNumber = builder.pageNumber;
        this.pageSize = builder.pageSize;
        this.queryTimestamp = Objects.requireNonNull(builder.queryTimestamp, "queryTimestamp must not be null");
        this.queryDurationMs = builder.queryDurationMs;
        this.hasMore = builder.hasMore;
    }

    // Getters
    public List<T> getItems() { return items; }
    public long getTotalCount() { return totalCount; }
    public int getPageNumber() { return pageNumber; }
    public int getPageSize() { return pageSize; }
    public Instant getQueryTimestamp() { return queryTimestamp; }
    public long getQueryDurationMs() { return queryDurationMs; }
    public boolean hasMore() { return hasMore; }
    public boolean isEmpty() { return items.isEmpty(); }
    public int getItemCount() { return items.size(); }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    @Override
    public String toString() {
        return "InvestigationResult{" +
                "itemCount=" + items.size() +
                ", totalCount=" + totalCount +
                ", pageNumber=" + pageNumber +
                ", pageSize=" + pageSize +
                ", queryDurationMs=" + queryDurationMs +
                ", hasMore=" + hasMore +
                '}';
    }

    public static final class Builder<T> {
        private List<T> items;
        private long totalCount;
        private int pageNumber;
        private int pageSize;
        private Instant queryTimestamp;
        private long queryDurationMs;
        private boolean hasMore;

        public Builder<T> items(List<T> items) {
            this.items = items;
            return this;
        }

        public Builder<T> totalCount(long totalCount) {
            this.totalCount = totalCount;
            return this;
        }

        public Builder<T> pageNumber(int pageNumber) {
            this.pageNumber = pageNumber;
            return this;
        }

        public Builder<T> pageSize(int pageSize) {
            this.pageSize = pageSize;
            return this;
        }

        public Builder<T> queryTimestamp(Instant queryTimestamp) {
            this.queryTimestamp = queryTimestamp;
            return this;
        }

        public Builder<T> queryDurationMs(long queryDurationMs) {
            this.queryDurationMs = queryDurationMs;
            return this;
        }

        public Builder<T> hasMore(boolean hasMore) {
            this.hasMore = hasMore;
            return this;
        }

        public InvestigationResult<T> build() {
            return new InvestigationResult<>(this);
        }
    }
}
