package com.eventore.inspect.kafka;

import com.eventore.connector.kafka.KafkaClientSupport;
import com.eventore.connector.spi.PublishRequest;
import com.eventore.domain.ConnectionProfile;
import com.eventore.inspect.kafka.KafkaAdminModels.AlterTopicConfigsRequest;
import com.eventore.inspect.kafka.KafkaAdminModels.CreateTopicRequest;
import com.eventore.inspect.kafka.KafkaAdminModels.FlushTopicRequest;
import com.eventore.inspect.kafka.KafkaAdminModels.KafkaAclEntry;
import com.eventore.inspect.kafka.KafkaAdminModels.PublishResult;
import com.eventore.inspect.kafka.KafkaAdminModels.ReplaceAclRequest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.DeleteAclsResult;
import org.apache.kafka.clients.admin.DeleteRecordsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.RecordsToDelete;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.acl.AccessControlEntry;
import org.apache.kafka.common.acl.AccessControlEntryFilter;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclBindingFilter;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.kafka.common.acl.AclPermissionType;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.resource.PatternType;
import org.apache.kafka.common.resource.ResourcePattern;
import org.apache.kafka.common.resource.ResourcePatternFilter;
import org.apache.kafka.common.resource.ResourceType;
import org.apache.kafka.common.security.auth.KafkaPrincipal;
import org.springframework.stereotype.Service;

@Service
public class KafkaAdminService {

    public List<String> adminFeatures() {
        return List.of(
                "publish-with-headers",
                "publish-flush",
                "topic-create",
                "topic-delete",
                "topic-flush",
                "topic-alter-configs",
                "acl-list",
                "acl-create",
                "acl-delete",
                "acl-replace");
    }

    public PublishResult publish(ConnectionProfile profile, PublishRequest request, boolean flushProducer)
            throws Exception {
        Properties props = KafkaClientSupport.producerProps(profile);
        try (KafkaProducer<String, byte[]> producer = new KafkaProducer<>(props)) {
            byte[] bytes = request.getPayload() != null
                    ? request.getPayload().getBytes(StandardCharsets.UTF_8)
                    : new byte[0];
            String key = headerValue(request, "key");
            Integer partition = parsePartition(request);
            ProducerRecord<String, byte[]> record =
                    partition != null
                            ? new ProducerRecord<>(request.getDestination(), partition, key, bytes)
                            : new ProducerRecord<>(request.getDestination(), key, bytes);
            applyRecordHeaders(record, request);
            RecordMetadata meta = producer.send(record).get();
            if (flushProducer) {
                producer.flush();
            }
            PublishResult result = new PublishResult();
            result.setTopic(meta.topic());
            result.setPartition(meta.partition());
            result.setOffset(meta.offset());
            return result;
        }
    }

    public void createTopic(ConnectionProfile profile, CreateTopicRequest req) throws Exception {
        try (AdminClient admin = AdminClient.create(KafkaClientSupport.clientProps(profile))) {
            NewTopic topic = new NewTopic(req.getName(), req.getPartitions(), req.getReplicationFactor());
            if (!req.getConfigs().isEmpty()) {
                topic.configs(req.getConfigs());
            }
            CreateTopicsResult result = admin.createTopics(List.of(topic));
            result.all().get();
        }
    }

    public void deleteTopic(ConnectionProfile profile, String topic) throws Exception {
        try (AdminClient admin = AdminClient.create(KafkaClientSupport.clientProps(profile))) {
            admin.deleteTopics(List.of(topic)).all().get();
        }
    }

    public Map<String, Object> flushTopic(ConnectionProfile profile, String topic, FlushTopicRequest req)
            throws Exception {
        try (AdminClient admin = AdminClient.create(KafkaClientSupport.clientProps(profile))) {
            Collection<TopicPartition> partitions = buildPartitions(admin, topic, req.getPartition());
            Map<TopicPartition, Long> endOffsets;
            Properties consumerProps =
                    KafkaClientSupport.consumerProps(profile, "eventore-flush-" + UUID.randomUUID());
            try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(consumerProps)) {
                endOffsets = consumer.endOffsets(partitions);
            }
            Map<TopicPartition, RecordsToDelete> toDelete = new HashMap<>();
            for (Map.Entry<TopicPartition, Long> e : endOffsets.entrySet()) {
                toDelete.put(e.getKey(), RecordsToDelete.beforeOffset(e.getValue()));
            }
            DeleteRecordsResult deleted = admin.deleteRecords(toDelete);
            Map<TopicPartition, Long> lowWatermarks = new HashMap<>();
            for (var entry : deleted.lowWatermarks().entrySet()) {
                lowWatermarks.put(entry.getKey(), entry.getValue().get().lowWatermark());
            }
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("topic", topic);
            report.put("partitions", lowWatermarks.size());
            report.put("lowWatermarks", formatWatermarks(lowWatermarks));
            return report;
        }
    }

    public void alterTopicConfigs(ConnectionProfile profile, String topic, AlterTopicConfigsRequest req)
            throws Exception {
        try (AdminClient admin = AdminClient.create(KafkaClientSupport.clientProps(profile))) {
            ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, topic);
            Map<ConfigResource, Collection<org.apache.kafka.clients.admin.AlterConfigOp>> configs =
                    Map.of(resource, toAlterOps(req.getConfigs()));
            admin.incrementalAlterConfigs(configs).all().get();
        }
    }

    public List<KafkaAclEntry> listAcls(
            ConnectionProfile profile,
            String resourceType,
            String resourceName,
            String principal)
            throws Exception {
        try (AdminClient admin = AdminClient.create(KafkaClientSupport.clientProps(profile))) {
            AclBindingFilter filter = buildAclFilter(resourceType, resourceName, principal, null, null);
            return admin.describeAcls(filter).values().get().stream()
                    .map(KafkaAdminService::toDto)
                    .toList();
        }
    }

    public void createAcl(ConnectionProfile profile, KafkaAclEntry entry) throws Exception {
        try (AdminClient admin = AdminClient.create(KafkaClientSupport.clientProps(profile))) {
            admin.createAcls(List.of(toBinding(entry))).all().get();
        }
    }

    public void deleteAcl(ConnectionProfile profile, KafkaAclEntry entry) throws Exception {
        try (AdminClient admin = AdminClient.create(KafkaClientSupport.clientProps(profile))) {
            AclBindingFilter filter = buildAclFilter(
                    entry.getResourceType(),
                    entry.getResourceName(),
                    entry.getPrincipal(),
                    entry.getOperation(),
                    entry.getPermissionType());
            DeleteAclsResult result = admin.deleteAcls(List.of(filter));
            result.all().get();
        }
    }

    public void replaceAcl(ConnectionProfile profile, ReplaceAclRequest req) throws Exception {
        deleteAcl(profile, req.getOldBinding());
        createAcl(profile, req.getNewBinding());
    }

    private static Collection<TopicPartition> buildPartitions(AdminClient admin, String topic, Integer partition)
            throws ExecutionException, InterruptedException {
        if (partition != null) {
            return List.of(new TopicPartition(topic, partition));
        }
        var desc = admin.describeTopics(List.of(topic)).allTopicNames().get().get(topic);
        List<TopicPartition> parts = new ArrayList<>();
        for (var p : desc.partitions()) {
            parts.add(new TopicPartition(topic, p.partition()));
        }
        return parts;
    }

    private static Map<String, String> formatWatermarks(Map<TopicPartition, Long> lowWatermarks) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<TopicPartition, Long> e : lowWatermarks.entrySet()) {
            out.put(e.getKey().partition() + "", String.valueOf(e.getValue()));
        }
        return out;
    }

    private static void applyRecordHeaders(ProducerRecord<String, byte[]> record, PublishRequest request) {
        if (request.getHeaders() == null) {
            return;
        }
        request.getHeaders().forEach((k, v) -> {
            if (v == null || isMetaHeader(k)) {
                return;
            }
            record.headers().add(k, v.getBytes(StandardCharsets.UTF_8));
        });
    }

    private static boolean isMetaHeader(String k) {
        return "key".equals(k) || "partition".equals(k) || "flush".equals(k);
    }

    private static String headerValue(PublishRequest request, String name) {
        if (request.getHeaders() == null) {
            return null;
        }
        return request.getHeaders().get(name);
    }

    private static Integer parsePartition(PublishRequest request) {
        String p = headerValue(request, "partition");
        if (p == null || p.isBlank()) {
            return null;
        }
        return Integer.parseInt(p);
    }

    private static List<org.apache.kafka.clients.admin.AlterConfigOp> toAlterOps(Map<String, String> configs) {
        List<org.apache.kafka.clients.admin.AlterConfigOp> ops = new ArrayList<>();
        for (Map.Entry<String, String> e : configs.entrySet()) {
            ops.add(new org.apache.kafka.clients.admin.AlterConfigOp(
                    new org.apache.kafka.clients.admin.ConfigEntry(e.getKey(), e.getValue()),
                    org.apache.kafka.clients.admin.AlterConfigOp.OpType.SET));
        }
        return ops;
    }

    private static KafkaAclEntry toDto(AclBinding binding) {
        KafkaAclEntry dto = new KafkaAclEntry();
        dto.setResourceType(binding.pattern().resourceType().name());
        dto.setResourceName(binding.pattern().name());
        dto.setPatternType(binding.pattern().patternType().name());
        AccessControlEntry ace = binding.entry();
        dto.setPrincipal(ace.principal());
        dto.setHost(ace.host());
        dto.setOperation(ace.operation().name());
        dto.setPermissionType(ace.permissionType().name());
        return dto;
    }

    private static AclBinding toBinding(KafkaAclEntry entry) {
        ResourcePattern pattern = new ResourcePattern(
                ResourceType.fromString(entry.getResourceType()),
                entry.getResourceName(),
                PatternType.fromString(entry.getPatternType() != null ? entry.getPatternType() : "LITERAL"));
        AccessControlEntry ace = new AccessControlEntry(
                entry.getPrincipal(),
                entry.getHost() != null ? entry.getHost() : "*",
                AclOperation.fromString(entry.getOperation()),
                AclPermissionType.fromString(entry.getPermissionType()));
        return new AclBinding(pattern, ace);
    }

    private static AclBindingFilter buildAclFilter(
            String resourceType,
            String resourceName,
            String principal,
            String operation,
            String permissionType) {
        ResourceType resourceTypeFilter =
                resourceType != null && !resourceType.isBlank()
                        ? ResourceType.fromString(resourceType)
                        : null;
        ResourcePatternFilter patternFilter = new ResourcePatternFilter(
                resourceTypeFilter,
                resourceName != null && !resourceName.isBlank() ? resourceName : null,
                PatternType.ANY);
        AccessControlEntryFilter entryFilter = new AccessControlEntryFilter(
                principal != null && !principal.isBlank() ? principal : null,
                null,
                operation != null && !operation.isBlank() ? AclOperation.fromString(operation) : AclOperation.ANY,
                permissionType != null && !permissionType.isBlank()
                        ? AclPermissionType.fromString(permissionType)
                        : AclPermissionType.ANY);
        return new AclBindingFilter(patternFilter, entryFilter);
    }
}
