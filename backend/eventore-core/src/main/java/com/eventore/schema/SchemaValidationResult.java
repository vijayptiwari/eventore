package com.eventore.schema;

import java.util.List;

/**
 * Result of validating a message payload against a registered schema.
 */
public record SchemaValidationResult(boolean valid, List<String> errors) {

    public static SchemaValidationResult success() {
        return new SchemaValidationResult(true, List.of());
    }

    public static SchemaValidationResult failure(List<String> errors) {
        return new SchemaValidationResult(false, errors != null ? errors : List.of());
    }

    public static SchemaValidationResult failure(String error) {
        return new SchemaValidationResult(false, List.of(error));
    }
}
