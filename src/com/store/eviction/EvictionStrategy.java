package com.store.eviction;

/**
 * Strategy interface for cache eviction algorithms.
 *
 * @param <K> the type of keys tracked
 */
public interface EvictionStrategy<K> {

    /**
     * Called when a key is newly inserted into the store.
     */
    void onInsert(K key);

    /**
     * Called when an existing key is read.
     */
    void onAccess(K key);

    /**
     * Called when an existing key's value is updated.
     */
    void onUpdate(K key);

    /**
     * Called when a key is removed or expired.
     */
    void onRemove(K key);

    /**
     * Selects and removes the key to be evicted according to the strategy.
     * Returns null if no elements are available for eviction.
     */
    K evict();

    /**
     * Clears all tracking state.
     */
    void clear();

    /**
     * Returns human-readable name of the policy.
     */
    String getName();
}
