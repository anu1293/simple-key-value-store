package com.store.exception;

public class CapacityExceededException extends KeyValueStoreException {
    public CapacityExceededException(String message) {
        super(message);
    }
}
