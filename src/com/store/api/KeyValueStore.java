package com.store.api;

import java.util.Optional;
import java.util.Set;

/**
 * Generic interface for an in-memory key-value store with Optimistic Concurrency Control (OCC).
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
     * Retrieves the value snapshot along with its version metadata for Optimistic Concurrency Control.
     * Returns an empty Optional if key not found or expired.
     */
    Optional<VersionedValue<V>> getVersioned(K key);

    /**
     * Atomically sets the value to newValue if the current value equals expectedValue.
     * Uses default TTL.
     *
     * @return true if successful, false if expectedValue did not match or key not found
     */
    default boolean compareAndSet(K key, V expectedValue, V newValue) {
        return compareAndSet(key, expectedValue, newValue, -1L);
    }

    /**
     * Atomically sets the value to newValue with custom TTL if the current value equals expectedValue.
     *
     * @return true if successful, false if expectedValue did not match or key not found
     */
    boolean compareAndSet(K key, V expectedValue, V newValue, long ttlMillis);

    /**
     * Atomically updates the value if the current entry version matches expectedVersion.
     * Uses default TTL.
     *
     * @return true if update succeeded, false if version mismatched or entry not found/expired
     */
    default boolean putIfVersion(K key, V newValue, long expectedVersion) {
        return putIfVersion(key, newValue, -1L, expectedVersion);
    }

    /**
     * Atomically updates the value with custom TTL if the current entry version matches expectedVersion.
     *
     * @return true if update succeeded, false if version mismatched or entry not found/expired
     */
    boolean putIfVersion(K key, V newValue, long ttlMillis, long expectedVersion);

    /**
     * Atomically inserts the key-value pair only if the key is not already present or has expired.
     * Uses default TTL.
     *
     * @return true if inserted, false if key already exists
     */
    default boolean putIfAbsent(K key, V value) {
        return putIfAbsent(key, value, -1L);
    }

    /**
     * Atomically inserts the key-value pair with custom TTL only if the key is not already present or has expired.
     *
     * @return true if inserted, false if key already exists
     */
    boolean putIfAbsent(K key, V value, long ttlMillis);

    /**
     * Atomically removes the entry only if its current version matches expectedVersion.
     *
     * @return true if removed, false if version mismatched or entry not found/expired
     */
    boolean removeIfVersion(K key, long expectedVersion);

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
