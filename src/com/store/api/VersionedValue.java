package com.store.api;

import java.util.Objects;

/**
 * Snapshot representing a value and its version metadata for Optimistic Concurrency Control (OCC).
 *
 * @param <V> Value type
 */
public final class VersionedValue<V> {
    private final V value;
    private final long version;
    private final long createdAt;
    private final long lastAccessedAt;
    private final long remainingTtlMillis;

    public VersionedValue(V value, long version, long createdAt, long lastAccessedAt, long remainingTtlMillis) {
        this.value = value;
        this.version = version;
        this.createdAt = createdAt;
        this.lastAccessedAt = lastAccessedAt;
        this.remainingTtlMillis = remainingTtlMillis;
    }

    public V getValue() {
        return value;
    }

    public long getVersion() {
        return version;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getLastAccessedAt() {
        return lastAccessedAt;
    }

    public long getRemainingTtlMillis() {
        return remainingTtlMillis;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VersionedValue<?> that = (VersionedValue<?>) o;
        return version == that.version && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, version);
    }

    @Override
    public String toString() {
        return "VersionedValue{" +
                "value=" + value +
                ", version=" + version +
                ", remainingTtl=" + (remainingTtlMillis < 0 ? "INFINITY" : remainingTtlMillis + "ms") +
                '}';
    }
}
