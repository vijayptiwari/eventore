package com.eventore.dataplane;

public class DataPlaneException extends RuntimeException {

    public DataPlaneException(String message) {
        super(message);
    }

    public DataPlaneException(String message, Throwable cause) {
        super(message, cause);
    }
}
