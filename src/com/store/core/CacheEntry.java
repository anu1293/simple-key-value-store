package com.store.core;

/**
 * Represents a key-value entry with metadata for access time and expiration.
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

    public CacheEntry(K key, V value, long ttlMillis) {
        this.key = key;
        this.value = value;
        this.createdAt = System.currentTimeMillis();
        this.lastAccessedAt = this.createdAt;
        this.ttlMillis = ttlMillis;
        this.expirationTime = ttlMillis > 0 ? this.createdAt + ttlMillis : -1L;
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

    public boolean isExpired() {
        return expirationTime > 0 && System.currentTimeMillis() >= expirationTime;
    }

    public long getRemainingTtlMillis() {
        if (expirationTime <= 0) {
            return -1L;
        }
        return Math.max(0L, expirationTime - System.currentTimeMillis());
    }

    @Override
    public String toString() {
        return "CacheEntry{" +
                "key=" + key +
                ", value=" + value +
                ", isExpired=" + isExpired() +
                ", remainingTtl=" + (getRemainingTtlMillis() == -1 ? "INFINITY" : getRemainingTtlMillis() + "ms") +
                '}';
    }
}
