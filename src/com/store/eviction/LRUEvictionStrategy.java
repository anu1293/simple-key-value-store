package com.store.eviction;

import java.util.HashMap;
import java.util.Map;

/**
 * Implements Least Recently Used (LRU) eviction strategy in O(1) time complexity
 * using a Doubly Linked List and HashMap.
 *
 * @param <K> Key type
 */
public class LRUEvictionStrategy<K> implements EvictionStrategy<K> {

    private static class Node<K> {
        final K key;
        Node<K> prev;
        Node<K> next;

        Node(K key) {
            this.key = key;
        }
    }

    private final Map<K, Node<K>> nodeMap = new HashMap<>();
    private final Node<K> head;
    private final Node<K> tail;

    public LRUEvictionStrategy() {
        head = new Node<>(null);
        tail = new Node<>(null);
        head.next = tail;
        tail.prev = head;
    }

    @Override
    public void onInsert(K key) {
        if (nodeMap.containsKey(key)) {
            onAccess(key);
            return;
        }
        Node<K> node = new Node<>(key);
        nodeMap.put(key, node);
        addToHead(node);
    }

    @Override
    public void onAccess(K key) {
        Node<K> node = nodeMap.get(key);
        if (node != null) {
            removeNode(node);
            addToHead(node);
        }
    }

    @Override
    public void onUpdate(K key) {
        onAccess(key);
    }

    @Override
    public void onRemove(K key) {
        Node<K> node = nodeMap.remove(key);
        if (node != null) {
            removeNode(node);
        }
    }

    @Override
    public K evict() {
        if (nodeMap.isEmpty() || tail.prev == head) {
            return null;
        }
        Node<K> lruNode = tail.prev;
        removeNode(lruNode);
        nodeMap.remove(lruNode.key);
        return lruNode.key;
    }

    @Override
    public void clear() {
        nodeMap.clear();
        head.next = tail;
        tail.prev = head;
    }

    @Override
    public String getName() {
        return "LRU (Least Recently Used)";
    }

    private void addToHead(Node<K> node) {
        node.next = head.next;
        node.prev = head;
        head.next.prev = node;
        head.next = node;
    }

    private void removeNode(Node<K> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }
}
