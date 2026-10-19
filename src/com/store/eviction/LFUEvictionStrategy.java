package com.store.eviction;

import java.util.HashMap;
import java.util.Map;

/**
 * Implements Least Frequently Used (LFU) eviction strategy in O(1) time complexity.
 * Uses frequency buckets with doubly-linked lists. Ties are broken using LRU order.
 *
 * @param <K> Key type
 */
public class LFUEvictionStrategy<K> implements EvictionStrategy<K> {

    private static class Node<K> {
        final K key;
        int frequency;
        Node<K> prev;
        Node<K> next;

        Node(K key, int frequency) {
            this.key = key;
            this.frequency = frequency;
        }
    }

    private static class DoublyLinkedList<K> {
        final Node<K> head;
        final Node<K> tail;
        int size;

        DoublyLinkedList() {
            head = new Node<>(null, 0);
            tail = new Node<>(null, 0);
            head.next = tail;
            tail.prev = head;
            size = 0;
        }

        void addFirst(Node<K> node) {
            node.next = head.next;
            node.prev = head;
            head.next.prev = node;
            head.next = node;
            size++;
        }

        void remove(Node<K> node) {
            node.prev.next = node.next;
            node.next.prev = node.prev;
            size--;
        }

        Node<K> removeLast() {
            if (size == 0 || tail.prev == head) {
                return null;
            }
            Node<K> last = tail.prev;
            remove(last);
            return last;
        }

        boolean isEmpty() {
            return size == 0;
        }
    }

    private final Map<K, Node<K>> nodeMap = new HashMap<>();
    private final Map<Integer, DoublyLinkedList<K>> freqBuckets = new HashMap<>();
    private int minFrequency = 0;

    @Override
    public void onInsert(K key) {
        if (nodeMap.containsKey(key)) {
            onAccess(key);
            return;
        }

        Node<K> node = new Node<>(key, 1);
        nodeMap.put(key, node);
        freqBuckets.computeIfAbsent(1, k -> new DoublyLinkedList<>()).addFirst(node);
        minFrequency = 1;
    }

    @Override
    public void onAccess(K key) {
        Node<K> node = nodeMap.get(key);
        if (node == null) {
            return;
        }

        int oldFreq = node.frequency;
        DoublyLinkedList<K> currentList = freqBuckets.get(oldFreq);
        currentList.remove(node);

        if (currentList.isEmpty()) {
            freqBuckets.remove(oldFreq);
            if (minFrequency == oldFreq) {
                minFrequency++;
            }
        }

        node.frequency++;
        freqBuckets.computeIfAbsent(node.frequency, k -> new DoublyLinkedList<>()).addFirst(node);
    }

    @Override
    public void onUpdate(K key) {
        onAccess(key);
    }

    @Override
    public void onRemove(K key) {
        Node<K> node = nodeMap.remove(key);
        if (node == null) {
            return;
        }

        DoublyLinkedList<K> currentList = freqBuckets.get(node.frequency);
        if (currentList != null) {
            currentList.remove(node);
            if (currentList.isEmpty()) {
                freqBuckets.remove(node.frequency);
                if (minFrequency == node.frequency) {
                    recalculateMinFrequency();
                }
            }
        }

        if (nodeMap.isEmpty()) {
            minFrequency = 0;
        }
    }

    @Override
    public K evict() {
        if (nodeMap.isEmpty()) {
            return null;
        }

        DoublyLinkedList<K> minList = freqBuckets.get(minFrequency);
        if (minList == null || minList.isEmpty()) {
            recalculateMinFrequency();
            minList = freqBuckets.get(minFrequency);
        }

        if (minList == null || minList.isEmpty()) {
            return null;
        }

        Node<K> victim = minList.removeLast();
        if (victim == null) {
            return null;
        }

        nodeMap.remove(victim.key);

        if (minList.isEmpty()) {
            freqBuckets.remove(minFrequency);
            recalculateMinFrequency();
        }

        return victim.key;
    }

    private void recalculateMinFrequency() {
        if (nodeMap.isEmpty()) {
            minFrequency = 0;
        } else {
            minFrequency = freqBuckets.keySet().stream().min(Integer::compare).orElse(0);
        }
    }

    @Override
    public void clear() {
        nodeMap.clear();
        freqBuckets.clear();
        minFrequency = 0;
    }

    @Override
    public String getName() {
        return "LFU (Least Frequently Used)";
    }
}
