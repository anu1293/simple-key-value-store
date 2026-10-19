package com.store.api;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks runtime metrics and statistics for the key-value store.
 */
public class StoreStats {
    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);
    private final AtomicLong puts = new AtomicLong(0);
    private final AtomicLong evictions = new AtomicLong(0);
    private final AtomicLong expirations = new AtomicLong(0);
    private final AtomicLong casSuccesses = new AtomicLong(0);
    private final AtomicLong casFailures = new AtomicLong(0);

    public void recordHit() {
        hits.incrementAndGet();
    }

    public void recordMiss() {
        misses.incrementAndGet();
    }

    public void recordPut() {
        puts.incrementAndGet();
    }

    public void recordEviction() {
        evictions.incrementAndGet();
    }

    public void recordExpiration() {
        expirations.incrementAndGet();
    }

    public void recordExpirations(long count) {
        expirations.addAndGet(count);
    }

    public void recordCasSuccess() {
        casSuccesses.incrementAndGet();
    }

    public void recordCasFailure() {
        casFailures.incrementAndGet();
    }

    public long getHits() {
        return hits.get();
    }

    public long getMisses() {
        return misses.get();
    }

    public long getPuts() {
        return puts.get();
    }

    public long getEvictions() {
        return evictions.get();
    }

    public long getExpirations() {
        return expirations.get();
    }

    public long getCasSuccesses() {
        return casSuccesses.get();
    }

    public long getCasFailures() {
        return casFailures.get();
    }

    public double getHitRate() {
        long total = hits.get() + misses.get();
        if (total == 0) return 0.0;
        return (double) hits.get() / total * 100.0;
    }

    public void reset() {
        hits.set(0);
        misses.set(0);
        puts.set(0);
        evictions.set(0);
        expirations.set(0);
        casSuccesses.set(0);
        casFailures.set(0);
    }

    @Override
    public String toString() {
        long totalRequests = hits.get() + misses.get();
        return String.format(
            "StoreStats [Puts: %d | Hits: %d | Misses: %d | Total Reads: %d | Hit Rate: %.2f%% | Evictions: %d | Expirations: %d | CAS Success: %d | CAS Failure: %d]",
            puts.get(), hits.get(), misses.get(), totalRequests, getHitRate(), evictions.get(), expirations.get(), casSuccesses.get(), casFailures.get()
        );
    }
}
