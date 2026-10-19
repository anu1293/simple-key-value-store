package com.store.exception;

public class KeyValueStoreException extends RuntimeException {
    public KeyValueStoreException(String message) {
        super(message);
    }

    public KeyValueStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
