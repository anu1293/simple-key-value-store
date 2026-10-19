package com.store.api;

import java.util.Optional;
import java.util.Set;

/**
 * Generic interface for an in-memory key-value store.
 *
 * @param <K> the type of keys maintained by this store
 * @param <V> the type of mapped values
 */
public interface KeyValueStore<K, V> extends AutoCloseable {

    /**
     * Associates the specified value with the specified key using the default TTL (if configured).
     */
    void put(K key, V value);

    /**
     * Associates the specified value with the specified key with a custom Time-To-Live in milliseconds.
     * If ttlMillis <= 0, the entry never expires.
     */
    void put(K key, V value, long ttlMillis);

    /**
     * Retrieves the value associated with the specified key.
     * Returns an empty Optional if key not found or expired.
     */
    Optional<V> get(K key);

    /**
     * Retrieves the value associated with the key or defaultValue if not found.
     */
    default V getOrDefault(K key, V defaultValue) {
        return get(key).orElse(defaultValue);
    }

    /**
     * Removes the key-value mapping if present.
     * Returns true if the key was present and removed.
     */
    boolean remove(K key);

    /**
     * Checks if the store contains a non-expired entry for the key.
     */
    boolean containsKey(K key);

    /**
     * Returns the current number of non-expired entries in the store.
     */
    int size();

    /**
     * Returns true if store contains no non-expired entries.
     */
    default boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Clears all entries from the store.
     */
    void clear();

    /**
     * Returns a snapshot set of all active non-expired keys.
     */
    Set<K> keySet();

    /**
     * Returns the metrics and statistics tracker.
     */
    StoreStats getStats();

    /**
     * Closes the store and releases background resources (e.g., active cleanup threads).
     */
    @Override
    void close();
}
