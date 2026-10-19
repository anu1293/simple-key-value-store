package com.store.core;

import com.store.api.KeyValueStore;
import com.store.api.StoreStats;
import com.store.api.VersionedValue;
import com.store.eviction.EvictionPolicy;
import com.store.eviction.EvictionStrategy;
import com.store.eviction.FIFOEvictionStrategy;
import com.store.eviction.LFUEvictionStrategy;
import com.store.eviction.LRUEvictionStrategy;
import com.store.exception.CapacityExceededException;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe In-Memory Key-Value Store supporting TTL, pluggable eviction policies (LRU/LFU/FIFO),
 * and Optimistic Concurrency Control (OCC) / atomic operations.
 *
 * @param <K> Key type
 * @param <V> Value type
 */
public class InMemoryKeyValueStore<K, V> implements KeyValueStore<K, V> {

    private final Map<K, CacheEntry<K, V>> dataStore = new HashMap<>();
    private final EvictionStrategy<K> evictionStrategy;
    private final int capacity; // <= 0 means unbounded
    private final long defaultTtlMillis; // <= 0 means no default TTL
    private final StoreStats stats = new StoreStats();
    private final ReentrantLock lock = new ReentrantLock();

    private final ScheduledExecutorService cleanupScheduler;
    private final boolean activeExpirationEnabled;

    public InMemoryKeyValueStore(int capacity,
                                 EvictionPolicy policy,
                                 long defaultTtlMillis,
                                 boolean activeExpirationEnabled,
                                 long cleanupIntervalMillis) {
        this.capacity = capacity;
        this.defaultTtlMillis = defaultTtlMillis;
        this.activeExpirationEnabled = activeExpirationEnabled;
        this.evictionStrategy = createStrategy(policy);

        if (activeExpirationEnabled && cleanupIntervalMillis > 0) {
            ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, r -> {
                Thread t = new Thread(r, "kv-store-ttl-cleanup");
                t.setDaemon(true);
                return t;
            });
            executor.setRemoveOnCancelPolicy(true);
            this.cleanupScheduler = executor;
            this.cleanupScheduler.scheduleAtFixedRate(
                    this::cleanExpiredEntries,
                    cleanupIntervalMillis,
                    cleanupIntervalMillis,
                    TimeUnit.MILLISECONDS
            );
        } else {
            this.cleanupScheduler = null;
        }
    }

    private EvictionStrategy<K> createStrategy(EvictionPolicy policy) {
        if (policy == null) {
            return new LRUEvictionStrategy<>();
        }
        return switch (policy) {
            case LFU -> new LFUEvictionStrategy<>();
            case FIFO -> new FIFOEvictionStrategy<>();
            case LRU -> new LRUEvictionStrategy<>();
        };
    }

    @Override
    public void put(K key, V value) {
        put(key, value, defaultTtlMillis);
    }

    @Override
    public void put(K key, V value, long ttlMillis) {
        Objects.requireNonNull(key, "Key cannot be null");
        lock.lock();
        try {
            CacheEntry<K, V> existing = dataStore.get(key);

            if (existing != null) {
                if (existing.isExpired()) {
                    // Stale entry being overwritten
                    stats.recordExpiration();
                    CacheEntry<K, V> newEntry = new CacheEntry<>(key, value, ttlMillis, 1L);
                    dataStore.put(key, newEntry);
                    evictionStrategy.onUpdate(key);
                    stats.recordPut();
                    return;
                }
                // Key exists: update value, TTL, and increment version
                long nextVersion = existing.getVersion() + 1;
                CacheEntry<K, V> newEntry = new CacheEntry<>(key, value, ttlMillis, nextVersion);
                dataStore.put(key, newEntry);
                evictionStrategy.onUpdate(key);
                stats.recordPut();
                return;
            }

            // Key does not exist: check capacity and evict if needed
            ensureCapacityForNewEntry();

            CacheEntry<K, V> entry = new CacheEntry<>(key, value, ttlMillis, 1L);
            dataStore.put(key, entry);
            evictionStrategy.onInsert(key);
            stats.recordPut();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Optional<V> get(K key) {
        Objects.requireNonNull(key, "Key cannot be null");
        lock.lock();
        try {
            CacheEntry<K, V> entry = dataStore.get(key);

            if (entry == null) {
                stats.recordMiss();
                return Optional.empty();
            }

            if (entry.isExpired()) {
                // Lazy passive eviction on read
                dataStore.remove(key);
                evictionStrategy.onRemove(key);
                stats.recordExpiration();
                stats.recordMiss();
                return Optional.empty();
            }

            entry.recordAccess();
            evictionStrategy.onAccess(key);
            stats.recordHit();
            return Optional.ofNullable(entry.getValue());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Optional<VersionedValue<V>> getVersioned(K key) {
        Objects.requireNonNull(key, "Key cannot be null");
        lock.lock();
        try {
            CacheEntry<K, V> entry = dataStore.get(key);

            if (entry == null) {
                stats.recordMiss();
                return Optional.empty();
            }

            if (entry.isExpired()) {
                dataStore.remove(key);
                evictionStrategy.onRemove(key);
                stats.recordExpiration();
                stats.recordMiss();
                return Optional.empty();
            }

            entry.recordAccess();
            evictionStrategy.onAccess(key);
            stats.recordHit();
            return Optional.of(entry.toVersionedValue());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean compareAndSet(K key, V expectedValue, V newValue, long ttlMillis) {
        Objects.requireNonNull(key, "Key cannot be null");
        lock.lock();
        try {
            CacheEntry<K, V> existing = dataStore.get(key);
            if (existing == null) {
                stats.recordMiss();
                stats.recordCasFailure();
                return false;
            }
            if (existing.isExpired()) {
                dataStore.remove(key);
                evictionStrategy.onRemove(key);
                stats.recordExpiration();
                stats.recordMiss();
                stats.recordCasFailure();
                return false;
            }
            if (!Objects.equals(existing.getValue(), expectedValue)) {
                stats.recordCasFailure();
                return false;
            }

            long effectiveTtl = ttlMillis <= 0 ? defaultTtlMillis : ttlMillis;
            long nextVersion = existing.getVersion() + 1;
            CacheEntry<K, V> updated = new CacheEntry<>(key, newValue, effectiveTtl, nextVersion);
            dataStore.put(key, updated);
            evictionStrategy.onUpdate(key);
            stats.recordCasSuccess();
            stats.recordPut();
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean putIfVersion(K key, V newValue, long ttlMillis, long expectedVersion) {
        Objects.requireNonNull(key, "Key cannot be null");
        lock.lock();
        try {
            CacheEntry<K, V> existing = dataStore.get(key);
            if (existing == null) {
                stats.recordMiss();
                stats.recordCasFailure();
                return false;
            }
            if (existing.isExpired()) {
                dataStore.remove(key);
                evictionStrategy.onRemove(key);
                stats.recordExpiration();
                stats.recordMiss();
                stats.recordCasFailure();
                return false;
            }
            if (existing.getVersion() != expectedVersion) {
                stats.recordCasFailure();
                return false;
            }

            long effectiveTtl = ttlMillis <= 0 ? defaultTtlMillis : ttlMillis;
            CacheEntry<K, V> updated = new CacheEntry<>(key, newValue, effectiveTtl, expectedVersion + 1);
            dataStore.put(key, updated);
            evictionStrategy.onUpdate(key);
            stats.recordCasSuccess();
            stats.recordPut();
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean putIfAbsent(K key, V value, long ttlMillis) {
        Objects.requireNonNull(key, "Key cannot be null");
        lock.lock();
        try {
            CacheEntry<K, V> existing = dataStore.get(key);
            if (existing != null) {
                if (!existing.isExpired()) {
                    return false;
                }
                dataStore.remove(key);
                evictionStrategy.onRemove(key);
                stats.recordExpiration();
            }

            long effectiveTtl = ttlMillis <= 0 ? defaultTtlMillis : ttlMillis;
            ensureCapacityForNewEntry();

            CacheEntry<K, V> entry = new CacheEntry<>(key, value, effectiveTtl, 1L);
            dataStore.put(key, entry);
            evictionStrategy.onInsert(key);
            stats.recordPut();
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean removeIfVersion(K key, long expectedVersion) {
        Objects.requireNonNull(key, "Key cannot be null");
        lock.lock();
        try {
            CacheEntry<K, V> existing = dataStore.get(key);
            if (existing == null) {
                stats.recordCasFailure();
                return false;
            }
            if (existing.isExpired()) {
                dataStore.remove(key);
                evictionStrategy.onRemove(key);
                stats.recordExpiration();
                stats.recordCasFailure();
                return false;
            }
            if (existing.getVersion() != expectedVersion) {
                stats.recordCasFailure();
                return false;
            }

            dataStore.remove(key);
            evictionStrategy.onRemove(key);
            stats.recordCasSuccess();
            return true;
        } finally {
            lock.unlock();
        }
    }

    private void ensureCapacityForNewEntry() {
        if (capacity > 0 && dataStore.size() >= capacity) {
            // First try to prune an expired key before evicting a fresh one
            if (!pruneOneExpiredEntry()) {
                K victim = evictionStrategy.evict();
                if (victim == null) {
                    throw new CapacityExceededException("Store capacity reached (" + capacity + ") and no victim available for eviction.");
                }
                dataStore.remove(victim);
                stats.recordEviction();
            }
        }
    }

    @Override
    public boolean remove(K key) {
        Objects.requireNonNull(key, "Key cannot be null");
        lock.lock();
        try {
            CacheEntry<K, V> entry = dataStore.remove(key);
            if (entry != null) {
                evictionStrategy.onRemove(key);
                if (entry.isExpired()) {
                    stats.recordExpiration();
                }
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean containsKey(K key) {
        Objects.requireNonNull(key, "Key cannot be null");
        lock.lock();
        try {
            CacheEntry<K, V> entry = dataStore.get(key);
            if (entry == null) {
                return false;
            }
            if (entry.isExpired()) {
                dataStore.remove(key);
                evictionStrategy.onRemove(key);
                stats.recordExpiration();
                return false;
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int size() {
        lock.lock();
        try {
            cleanExpiredEntries();
            return dataStore.size();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void clear() {
        lock.lock();
        try {
            dataStore.clear();
            evictionStrategy.clear();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Set<K> keySet() {
        lock.lock();
        try {
            cleanExpiredEntries();
            return new HashSet<>(dataStore.keySet());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public StoreStats getStats() {
        return stats;
    }

    public int getCapacity() {
        return capacity;
    }

    public String getEvictionPolicyName() {
        return evictionStrategy.getName();
    }

    /**
     * Active expiration cleanup: removes all expired keys from the store.
     * Can be invoked manually or scheduled by the background thread.
     */
    public int cleanExpiredEntries() {
        lock.lock();
        try {
            List<K> expiredKeys = new ArrayList<>();
            for (Map.Entry<K, CacheEntry<K, V>> entry : dataStore.entrySet()) {
                if (entry.getValue().isExpired()) {
                    expiredKeys.add(entry.getKey());
                }
            }

            for (K key : expiredKeys) {
                dataStore.remove(key);
                evictionStrategy.onRemove(key);
            }

            if (!expiredKeys.isEmpty()) {
                stats.recordExpirations(expiredKeys.size());
            }

            return expiredKeys.size();
        } finally {
            lock.unlock();
        }
    }

    private boolean pruneOneExpiredEntry() {
        for (Map.Entry<K, CacheEntry<K, V>> entry : dataStore.entrySet()) {
            if (entry.getValue().isExpired()) {
                K key = entry.getKey();
                dataStore.remove(key);
                evictionStrategy.onRemove(key);
                stats.recordExpiration();
                return true;
            }
        }
        return false;
    }

    @Override
    public void close() {
        if (cleanupScheduler != null) {
            cleanupScheduler.shutdown();
            try {
                if (!cleanupScheduler.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                    cleanupScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                cleanupScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
