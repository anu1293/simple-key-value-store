package com.store.eviction;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Implements First-In First-Out (FIFO) eviction strategy.
 *
 * @param <K> Key type
 */
public class FIFOEvictionStrategy<K> implements EvictionStrategy<K> {

    private final Set<K> set = new LinkedHashSet<>();

    @Override
    public void onInsert(K key) {
        set.add(key);
    }

    @Override
    public void onAccess(K key) {
        // In FIFO, access order does not change eviction priority.
    }

    @Override
    public void onUpdate(K key) {
        // In FIFO, update does not change initial arrival priority.
    }

    @Override
    public void onRemove(K key) {
        set.remove(key);
    }

    @Override
    public K evict() {
        Iterator<K> iterator = set.iterator();
        if (!iterator.hasNext()) {
            return null;
        }
        K oldest = iterator.next();
        iterator.remove();
        return oldest;
    }

    @Override
    public void clear() {
        set.clear();
    }

    @Override
    public String getName() {
        return "FIFO (First In First Out)";
    }
}
