package com.eventore.schema;

import java.util.Map;

/**
 * Reusable validator for inspecting outbound publish headers and verifying payload
 * compliance against registered schemas before message transmission.
 */
public final class SchemaPayloadValidator {

    private SchemaPayloadValidator() {}

    /**
     * Extracts schema ID from headers (checking x-eventore-schema-id and schema-id case-insensitively).
     */
    public static String extractSchemaIdHeader(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if ("x-eventore-schema-id".equalsIgnoreCase(entry.getKey())
                    || "schema-id".equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Validates an outbound message payload against schema referenced in headers, if present.
     */
    public static SchemaValidationResult validate(
            SchemaRegistryClient client,
            String registryUrl,
            String payload,
            Map<String, String> headers) {
        String schemaIdStr = extractSchemaIdHeader(headers);
        if (schemaIdStr == null || schemaIdStr.isBlank()) {
            return SchemaValidationResult.success();
        }
        int schemaId;
        try {
            schemaId = Integer.parseInt(schemaIdStr.trim());
        } catch (NumberFormatException e) {
            return SchemaValidationResult.failure("Invalid schema ID format: " + schemaIdStr);
        }
        if (client == null) {
            return SchemaValidationResult.success();
        }
        var metaOpt = client.getSchemaById(registryUrl, schemaId);
        if (metaOpt.isEmpty()) {
            return SchemaValidationResult.failure("Schema with ID " + schemaId + " not found in registry");
        }
        SchemaMetadata meta = metaOpt.get();
        if ("AVRO".equalsIgnoreCase(meta.schemaType())) {
            return AvroPayloadDecoder.validateJson(payload, meta.schemaContent());
        }
        return SchemaValidationResult.success();
    }
}
