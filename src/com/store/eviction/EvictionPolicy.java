package com.store.eviction;

public enum EvictionPolicy {
    LRU("Least Recently Used"),
    LFU("Least Frequently Used"),
    FIFO("First In First Out");

    private final String description;

    EvictionPolicy(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
