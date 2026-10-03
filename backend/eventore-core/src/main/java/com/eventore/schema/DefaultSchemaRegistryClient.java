package com.eventore.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultSchemaRegistryClient implements SchemaRegistryClient {

    private static final Logger log = LoggerFactory.getLogger(DefaultSchemaRegistryClient.class);
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(4);

    private final Map<Integer, SchemaMetadata> cache = new ConcurrentHashMap<>();
    private final Map<String, SchemaMetadata> subjectCache = new ConcurrentHashMap<>();
    private final AtomicInteger localIdGenerator = new AtomicInteger(1000);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String defaultRegistryUrl;

    public DefaultSchemaRegistryClient() {
        this(null);
    }

    public DefaultSchemaRegistryClient(String defaultRegistryUrl) {
        this(
                defaultRegistryUrl,
                HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build(),
                new ObjectMapper());
    }

    public DefaultSchemaRegistryClient(
            String defaultRegistryUrl, HttpClient httpClient, ObjectMapper objectMapper) {
        this.defaultRegistryUrl = sanitizeUrl(defaultRegistryUrl);
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<SchemaMetadata> getSchemaById(int schemaId) {
        return getSchemaById(defaultRegistryUrl, schemaId);
    }

    @Override
    public Optional<SchemaMetadata> getSchemaById(String registryUrl, int schemaId) {
        SchemaMetadata cached = cache.get(schemaId);
        if (cached != null) {
            return Optional.of(cached);
        }

        String effectiveUrl = sanitizeUrl(registryUrl != null ? registryUrl : defaultRegistryUrl);
        if (effectiveUrl == null) {
            return Optional.empty();
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(effectiveUrl + "/schemas/ids/" + schemaId))
                    .timeout(HTTP_TIMEOUT)
                    .header("Accept", "application/vnd.schemaregistry.v1+json, application/json")
                    .GET()
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode json = objectMapper.readTree(response.body());
                String schemaContent = json.path("schema").asText("");
                String schemaType = json.path("schemaType").asText("AVRO");
                SchemaMetadata meta = new SchemaMetadata(schemaId, null, null, schemaType, schemaContent);
                cache.put(schemaId, meta);
                return Optional.of(meta);
            } else if (response.statusCode() == 404) {
                log.debug("Schema ID {} not found in registry at {}", schemaId, effectiveUrl);
            } else {
                log.warn("Schema Registry returned HTTP {} for schema ID {}", response.statusCode(), schemaId);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch schema ID {} from {}: {}", schemaId, effectiveUrl, e.getMessage());
        }

        return Optional.empty();
    }

    @Override
    public Optional<SchemaMetadata> getLatestSchema(String registryUrl, String subject) {
        String effectiveUrl = sanitizeUrl(registryUrl != null ? registryUrl : defaultRegistryUrl);
        String cacheKey = (effectiveUrl != null ? effectiveUrl : "local") + ":" + subject;

        SchemaMetadata cached = subjectCache.get(cacheKey);
        if (cached != null) {
            return Optional.of(cached);
        }

        if (effectiveUrl == null) {
            return Optional.empty();
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(effectiveUrl + "/subjects/" + subject + "/versions/latest"))
                    .timeout(HTTP_TIMEOUT)
                    .header("Accept", "application/vnd.schemaregistry.v1+json, application/json")
                    .GET()
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode json = objectMapper.readTree(response.body());
                int schemaId = json.path("id").asInt();
                int version = json.path("version").asInt(1);
                String schemaContent = json.path("schema").asText("");
                String schemaType = json.path("schemaType").asText("AVRO");

                SchemaMetadata meta = new SchemaMetadata(schemaId, subject, version, schemaType, schemaContent);
                cache.put(schemaId, meta);
                subjectCache.put(cacheKey, meta);
                return Optional.of(meta);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch latest schema for subject {} from {}: {}", subject, effectiveUrl, e.getMessage());
        }

        return Optional.empty();
    }

    @Override
    public SchemaMetadata registerSchema(
            String registryUrl, String subject, String schemaContent, String schemaType) {
        String effectiveType = (schemaType == null || schemaType.isBlank()) ? "AVRO" : schemaType.toUpperCase();
        String effectiveUrl = sanitizeUrl(registryUrl != null ? registryUrl : defaultRegistryUrl);

        if (effectiveUrl != null) {
            try {
                String payload = objectMapper.writeValueAsString(Map.of(
                        "schema", schemaContent,
                        "schemaType", effectiveType));

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(effectiveUrl + "/subjects/" + subject + "/versions"))
                        .timeout(HTTP_TIMEOUT)
                        .header("Content-Type", "application/vnd.schemaregistry.v1+json")
                        .header("Accept", "application/vnd.schemaregistry.v1+json, application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build();

                HttpResponse<String> response =
                        httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode json = objectMapper.readTree(response.body());
                    int id = json.path("id").asInt();
                    SchemaMetadata meta = new SchemaMetadata(id, subject, null, effectiveType, schemaContent);
                    cache.put(id, meta);
                    subjectCache.put(effectiveUrl + ":" + subject, meta);
                    return meta;
                }
            } catch (Exception e) {
                log.warn("Remote schema registration failed against {}: {}. Falling back to local ID.", effectiveUrl, e.getMessage());
            }
        }

        int id = localIdGenerator.incrementAndGet();
        SchemaMetadata localMeta = new SchemaMetadata(id, subject, 1, effectiveType, schemaContent);
        cache.put(id, localMeta);
        subjectCache.put("local:" + subject, localMeta);
        return localMeta;
    }

    @Override
    public void registerLocalSchema(int schemaId, String schemaContent, String schemaType) {
        String effectiveType = (schemaType == null || schemaType.isBlank()) ? "AVRO" : schemaType.toUpperCase();
        SchemaMetadata meta = new SchemaMetadata(schemaId, null, null, effectiveType, schemaContent);
        cache.put(schemaId, meta);
    }

    @Override
    public Map<Integer, SchemaMetadata> getCachedSchemas() {
        return Collections.unmodifiableMap(cache);
    }

    @Override
    public void clearCache() {
        cache.clear();
        subjectCache.clear();
    }

    private static String sanitizeUrl(String url) {
        if (url == null || url.isBlank()) return null;
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
