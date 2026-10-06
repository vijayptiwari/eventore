package com.eventore.schema;

import java.util.Map;
import java.util.Optional;

/**
 * Client SPI for fetching, caching, and registering schemas against
 * Confluent Schema Registry, Apicurio, or an in-memory local catalog.
 */
public interface SchemaRegistryClient {

    /**
     * Resolves a schema by its global numeric ID from the default/global registry or local cache.
     */
    Optional<SchemaMetadata> getSchemaById(int schemaId);

    /**
     * Resolves a schema by its global numeric ID from the specified registry URL (or local cache).
     */
    Optional<SchemaMetadata> getSchemaById(String registryUrl, int schemaId);

    /**
     * Resolves the latest version of a schema for the specified subject.
     */
    Optional<SchemaMetadata> getLatestSchema(String registryUrl, String subject);

    /**
     * Registers a schema under a subject.
     */
    SchemaMetadata registerSchema(String registryUrl, String subject, String schemaContent, String schemaType);

    /**
     * Registers a schema locally with an explicit schema ID (useful for air-gapped dev & tests).
     */
    void registerLocalSchema(int schemaId, String schemaContent, String schemaType);

    /**
     * Returns an unmodifiable snapshot keyed by registry URL and schema ID.
     */
    Map<String, SchemaMetadata> getCachedSchemas();

    /**
     * Clears the schema cache.
     */
    void clearCache();
}
