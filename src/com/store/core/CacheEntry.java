package com.store.core;

import com.store.api.VersionedValue;

/**
 * Represents a key-value entry with metadata for access time, expiration, and versioning.
 *
 * @param <K> Key type
 * @param <V> Value type
 */
public class CacheEntry<K, V> {
    private final K key;
    private V value;
    private final long createdAt;
    private volatile long lastAccessedAt;
    private final long ttlMillis;
    private final long expirationTime; // -1 if no TTL
    private final long version;

    public CacheEntry(K key, V value, long ttlMillis) {
        this(key, value, ttlMillis, 1L);
    }

    public CacheEntry(K key, V value, long ttlMillis, long version) {
        this.key = key;
        this.value = value;
        this.createdAt = System.currentTimeMillis();
        this.lastAccessedAt = this.createdAt;
        this.ttlMillis = ttlMillis;
        this.expirationTime = ttlMillis > 0 ? this.createdAt + ttlMillis : -1L;
        this.version = version;
    }

    public K getKey() {
        return key;
    }

    public V getValue() {
        return value;
    }

    public void setValue(V value) {
        this.value = value;
        this.lastAccessedAt = System.currentTimeMillis();
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void recordAccess() {
        this.lastAccessedAt = System.currentTimeMillis();
    }

    public long getTtlMillis() {
        return ttlMillis;
    }

    public long getExpirationTime() {
        return expirationTime;
    }

    public long getVersion() {
        return version;
    }

    public boolean isExpired() {
        return expirationTime > 0 && System.currentTimeMillis() >= expirationTime;
    }

    public long getRemainingTtlMillis() {
        if (expirationTime <= 0) {
            return -1L;
        }
        return Math.max(0L, expirationTime - System.currentTimeMillis());
    }

    /**
     * Converts this cache entry into an immutable {@link VersionedValue} snapshot.
     */
    public VersionedValue<V> toVersionedValue() {
        return new VersionedValue<>(value, version, createdAt, lastAccessedAt, getRemainingTtlMillis());
    }

    @Override
    public String toString() {
        return "CacheEntry{" +
                "key=" + key +
                ", value=" + value +
                ", version=" + version +
                ", isExpired=" + isExpired() +
                ", remainingTtl=" + (getRemainingTtlMillis() == -1 ? "INFINITY" : getRemainingTtlMillis() + "ms") +
                '}';
    }
}
