package com.store.core;

import com.store.api.KeyValueStore;
import com.store.eviction.EvictionPolicy;

/**
 * Fluent builder for creating {@link InMemoryKeyValueStore} instances.
 *
 * @param <K> Key type
 * @param <V> Value type
 */
public class KeyValueStoreBuilder<K, V> {
    private int capacity = 100;
    private EvictionPolicy policy = EvictionPolicy.LRU;
    private long defaultTtlMillis = -1L;
    private boolean activeExpiration = true;
    private long cleanupIntervalMillis = 1000L;

    public static <K, V> KeyValueStoreBuilder<K, V> create() {
        return new KeyValueStoreBuilder<>();
    }

    public KeyValueStoreBuilder<K, V> capacity(int capacity) {
        this.capacity = capacity;
        return this;
    }

    public KeyValueStoreBuilder<K, V> evictionPolicy(EvictionPolicy policy) {
        this.policy = policy;
        return this;
    }

    public KeyValueStoreBuilder<K, V> defaultTtlMillis(long defaultTtlMillis) {
        this.defaultTtlMillis = defaultTtlMillis;
        return this;
    }

    public KeyValueStoreBuilder<K, V> enableActiveExpiration(long cleanupIntervalMillis) {
        this.activeExpiration = true;
        this.cleanupIntervalMillis = cleanupIntervalMillis;
        return this;
    }

    public KeyValueStoreBuilder<K, V> disableActiveExpiration() {
        this.activeExpiration = false;
        return this;
    }

    public KeyValueStore<K, V> build() {
        return new InMemoryKeyValueStore<>(
                capacity,
                policy,
                defaultTtlMillis,
                activeExpiration,
                cleanupIntervalMillis
        );
    }
}
