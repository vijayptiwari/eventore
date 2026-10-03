package com.eventore.service.store;

import com.eventore.domain.CloudProvider;
import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.ConnectionStoreType;
import com.eventore.domain.ProtocolType;
import com.eventore.domain.StreamPlatform;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kubernetes Custom Resource Definition (CRD) declarative store (REQ-101).
 * Represents connection profiles as 'kind: EventoreConnection' manifests.
 */
public class K8sCrdConnectionProfileStore implements ConnectionProfileStore {

    private static final Logger log = LoggerFactory.getLogger(K8sCrdConnectionProfileStore.class);
    public static final String API_VERSION = "eventore.com/v1alpha1";
    public static final String KIND = "EventoreConnection";

    private final Path crdDirectory;
    private final boolean enabled;
    private final ObjectMapper objectMapper;

    public K8sCrdConnectionProfileStore(Path crdDirectory, boolean enabled, ObjectMapper objectMapper) {
        this.crdDirectory = crdDirectory != null ? crdDirectory : Path.of("/data/crds");
        this.enabled = enabled;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public K8sCrdConnectionProfileStore(String crdDirectory, boolean enabled, ObjectMapper objectMapper) {
        this(crdDirectory != null ? Path.of(crdDirectory) : Path.of("/data/crds"), enabled, objectMapper);
    }

    @Override
    public ConnectionStoreType getType() {
        return ConnectionStoreType.K8S_CRD;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public Path getCrdDirectory() {
        return crdDirectory;
    }

    @Override
    public Map<String, ConnectionProfile> loadAll() {
        if (!isEnabled() || !Files.exists(crdDirectory)) {
            return new LinkedHashMap<>();
        }
        Map<String, ConnectionProfile> result = new LinkedHashMap<>();
        try (Stream<Path> stream = Files.list(crdDirectory)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return name.endsWith(".json") || name.endsWith(".yaml") || name.endsWith(".yml");
                    })
                    .forEach(file -> {
                        try {
                            byte[] bytes = Files.readAllBytes(file);
                            JsonNode root = objectMapper.readTree(bytes);
                            ConnectionProfile profile = parseCrdNode(root);
                            if (profile != null && profile.getId() != null) {
                                result.put(profile.getId(), profile);
                            }
                        } catch (Exception e) {
                            log.warn("Failed to parse CRD file {}: {}", file, e.getMessage());
                        }
                    });
            log.info("Loaded {} connection profile(s) from CRD directory {}", result.size(), crdDirectory);
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read CRD directory " + crdDirectory, e);
        }
    }

    @Override
    public void save(ConnectionProfile profile) {
        if (profile == null || profile.getId() == null || profile.getId().isBlank()) {
            throw new IllegalArgumentException("Connection profile id is required");
        }
        if (!isEnabled()) {
            return;
        }
        ConnectionProfileStore.validatePersistableCredentials(profile);

        try {
            Files.createDirectories(crdDirectory);
            ObjectNode crdNode = objectMapper.createObjectNode();
            crdNode.put("apiVersion", API_VERSION);
            crdNode.put("kind", KIND);

            ObjectNode metadata = crdNode.putObject("metadata");
            metadata.put("name", sanitizeK8sName(profile.getId()));
            ObjectNode labels = metadata.putObject("labels");
            labels.put("eventore.com/protocol", profile.getProtocol() != null ? profile.getProtocol().name() : "GENERIC");
            labels.put("eventore.com/managed-by", "eventore-control-plane");

            ObjectNode spec = crdNode.putObject("spec");
            spec.put("id", profile.getId());
            spec.put("name", profile.getName() != null ? profile.getName() : profile.getId());
            spec.put("protocol", profile.getProtocol() != null ? profile.getProtocol().name() : "GENERIC");
            spec.put("cloudProvider", profile.getCloudProvider() != null ? profile.getCloudProvider().name() : "ON_PREM");
            spec.put("streamPlatform", profile.getStreamPlatform() != null ? profile.getStreamPlatform().name() : "GENERIC");
            if (profile.getBrokerUrl() != null) {
                spec.put("brokerUrl", profile.getBrokerUrl());
            }

            if (profile.getProperties() != null && !profile.getProperties().isEmpty()) {
                ObjectNode propsNode = spec.putObject("properties");
                profile.getProperties().forEach(propsNode::put);
            }

            if (profile.getCredentials() != null && !profile.getCredentials().isEmpty()) {
                ObjectNode credsNode = spec.putObject("credentials");
                profile.getCredentials().forEach(credsNode::put);
            }

            Path targetFile = crdDirectory.resolve(sanitizeK8sName(profile.getId()) + ".json");
            byte[] bytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(crdNode);
            Files.write(targetFile, bytes);
            log.debug("Persisted Kubernetes CRD manifest for profile '{}' to {}", profile.getId(), targetFile);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to persist CRD connection profile to " + crdDirectory, e);
        }
    }

    @Override
    public void saveAll(Map<String, ConnectionProfile> profiles) {
        if (profiles != null) {
            for (ConnectionProfile profile : profiles.values()) {
                save(profile);
            }
        }
    }

    @Override
    public void delete(String id) {
        if (id == null || !isEnabled()) {
            return;
        }
        Path targetFile = crdDirectory.resolve(sanitizeK8sName(id) + ".json");
        try {
            Files.deleteIfExists(targetFile);
            log.debug("Deleted Kubernetes CRD manifest for profile '{}'", id);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to delete CRD file " + targetFile, e);
        }
    }

    @Override
    public ConnectionStoreInfo getInfo() {
        int count = 0;
        try {
            count = loadAll().size();
        } catch (Exception ignored) {
        }
        return new ConnectionStoreInfo(
                getType(),
                isEnabled(),
                count,
                false,
                crdDirectory.toAbsolutePath().toString(),
                Map.of(
                        "crdGroup", API_VERSION,
                        "kind", KIND,
                        "crdDirectory", crdDirectory.toString()));
    }

    private ConnectionProfile parseCrdNode(JsonNode root) {
        if (root == null || !root.has("spec")) {
            return null;
        }
        JsonNode spec = root.get("spec");
        ConnectionProfile profile = new ConnectionProfile();

        String id = spec.has("id") ? spec.get("id").asText() : null;
        if (id == null && root.has("metadata") && root.get("metadata").has("name")) {
            id = root.get("metadata").get("name").asText();
        }
        profile.setId(id);

        if (spec.has("name")) {
            profile.setName(spec.get("name").asText());
        }
        if (spec.has("protocol")) {
            try {
                profile.setProtocol(ProtocolType.valueOf(spec.get("protocol").asText()));
            } catch (Exception ignored) {
            }
        }
        if (spec.has("cloudProvider")) {
            try {
                profile.setCloudProvider(CloudProvider.valueOf(spec.get("cloudProvider").asText()));
            } catch (Exception ignored) {
            }
        }
        if (spec.has("streamPlatform")) {
            try {
                profile.setStreamPlatform(StreamPlatform.valueOf(spec.get("streamPlatform").asText()));
            } catch (Exception ignored) {
            }
        }
        if (spec.has("brokerUrl")) {
            profile.setBrokerUrl(spec.get("brokerUrl").asText());
        }

        if (spec.has("properties") && spec.get("properties").isObject()) {
            Map<String, String> props = new HashMap<>();
            spec.get("properties").fields().forEachRemaining(entry -> props.put(entry.getKey(), entry.getValue().asText()));
            profile.setProperties(props);
        }

        if (spec.has("credentials") && spec.get("credentials").isObject()) {
            Map<String, String> creds = new HashMap<>();
            spec.get("credentials").fields().forEachRemaining(entry -> creds.put(entry.getKey(), entry.getValue().asText()));
            profile.setCredentials(creds);
        }

        return profile;
    }

    private String sanitizeK8sName(String name) {
        if (name == null) return "connection";
        return name.toLowerCase().replaceAll("[^a-z0-9-.]", "-").replaceAll("^-+|-+$", "");
    }
}
