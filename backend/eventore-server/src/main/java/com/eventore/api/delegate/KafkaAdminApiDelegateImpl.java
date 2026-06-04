package com.eventore.api.delegate;

import com.eventore.api.generated.kafka.KafkaAdminApiDelegate;
import com.eventore.connector.spi.PublishRequest;
import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.ProtocolType;
import com.eventore.api.generated.kafka.model.KafkaAclEntry;
import com.eventore.api.generated.kafka.model.KafkaAlterTopicConfigsRequest;
import com.eventore.api.generated.kafka.model.KafkaCreateTopicRequest;
import com.eventore.api.generated.kafka.model.KafkaFlushTopicRequest;
import com.eventore.api.generated.kafka.model.KafkaPublishResult;
import com.eventore.api.generated.kafka.model.KafkaReplaceAclRequest;
import com.eventore.inspect.kafka.KafkaAdminModels;
import com.eventore.inspect.kafka.KafkaAdminService;
import com.eventore.security.Action;
import com.eventore.security.DeploymentModePolicy;
import com.eventore.service.AuditService;
import com.eventore.service.ConnectionRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnBean(KafkaAdminService.class)
public class KafkaAdminApiDelegateImpl implements KafkaAdminApiDelegate {

    private final ConnectionRegistry connectionRegistry;
    private final KafkaAdminService kafkaAdmin;
    private final DeploymentModePolicy policy;
    private final AuditService auditService;

    public KafkaAdminApiDelegateImpl(
            ConnectionRegistry connectionRegistry,
            KafkaAdminService kafkaAdmin,
            DeploymentModePolicy policy,
            AuditService auditService) {
        this.connectionRegistry = connectionRegistry;
        this.kafkaAdmin = kafkaAdmin;
        this.policy = policy;
        this.auditService = auditService;
    }

    @Override
    public ResponseEntity<Object> kafkaCapabilities(String connectionId) {
        requireKafka(connectionId);
        return ResponseEntity.ok(Map.of("features", kafkaAdmin.adminFeatures()));
    }

    @Override
    public ResponseEntity<KafkaPublishResult> kafkaPublish(
            String connectionId, Boolean flush, PublishRequest publishRequest) {
        policy.require(Action.PUBLISH);
        ConnectionProfile profile = requireKafka(connectionId);
        boolean flushProducer = Boolean.TRUE.equals(flush)
                || (publishRequest.getHeaders() != null
                        && "true".equalsIgnoreCase(publishRequest.getHeaders().get("flush")));
        int bytes = publishRequest.getPayload() != null
                ? publishRequest.getPayload().getBytes(StandardCharsets.UTF_8).length
                : 0;
        policy.validatePublishSize(bytes);
        try {
            KafkaAdminModels.PublishResult domainResult =
                    kafkaAdmin.publish(profile, publishRequest, flushProducer);
            KafkaPublishResult result = toKafkaPublishResult(domainResult);
            HttpServletRequest request = currentRequest();
            auditService.publish(
                    connectionId,
                    profile.getProtocol(),
                    publishRequest.getDestination(),
                    bytes,
                    request != null ? request.getHeader("User-Agent") : null);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
        }
    }

    @Override
    public ResponseEntity<Map<String, String>> kafkaCreateTopic(
            String connectionId, KafkaCreateTopicRequest kafkaCreateTopicRequest) {
        policy.require(Action.ADMIN_BROKER_OPS);
        ConnectionProfile profile = requireKafka(connectionId);
        if (kafkaCreateTopicRequest.getName() == null || kafkaCreateTopicRequest.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "topic name is required");
        }
        try {
            kafkaAdmin.createTopic(profile, toCreateTopicRequest(kafkaCreateTopicRequest));
            return ResponseEntity.ok(Map.of("status", "created", "topic", kafkaCreateTopicRequest.getName()));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
        }
    }

    @Override
    public ResponseEntity<Map<String, String>> kafkaDeleteTopic(String connectionId, String topic) {
        policy.require(Action.ADMIN_BROKER_OPS);
        try {
            kafkaAdmin.deleteTopic(requireKafka(connectionId), topic);
            return ResponseEntity.ok(Map.of("status", "deleted", "topic", topic));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
        }
    }

    @Override
    public ResponseEntity<Object> kafkaFlushTopic(
            String connectionId, String topic, KafkaFlushTopicRequest kafkaFlushTopicRequest) {
        policy.require(Action.ADMIN_BROKER_OPS);
        KafkaAdminModels.FlushTopicRequest req =
                kafkaFlushTopicRequest != null
                        ? toFlushTopicRequest(kafkaFlushTopicRequest)
                        : new KafkaAdminModels.FlushTopicRequest();
        try {
            return ResponseEntity.ok(kafkaAdmin.flushTopic(requireKafka(connectionId), topic, req));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
        }
    }

    @Override
    public ResponseEntity<Map<String, String>> kafkaAlterTopicConfigs(
            String connectionId, String topic, KafkaAlterTopicConfigsRequest kafkaAlterTopicConfigsRequest) {
        policy.require(Action.ADMIN_BROKER_OPS);
        try {
            kafkaAdmin.alterTopicConfigs(
                    requireKafka(connectionId), topic, toAlterTopicConfigsRequest(kafkaAlterTopicConfigsRequest));
            return ResponseEntity.ok(Map.of("status", "updated", "topic", topic));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
        }
    }

    @Override
    public ResponseEntity<List<KafkaAclEntry>> kafkaListAcls(
            String connectionId, String resourceType, String resourceName, String principal) {
        policy.require(Action.ADMIN_BROKER_OPS);
        try {
            return ResponseEntity.ok(kafkaAdmin.listAcls(requireKafka(connectionId), resourceType, resourceName, principal).stream()
                    .map(KafkaAdminApiDelegateImpl::toKafkaAclEntry)
                    .toList());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
        }
    }

    @Override
    public ResponseEntity<Map<String, String>> kafkaCreateAcl(
            String connectionId,
            String resourceType,
            String resourceName,
            String principal,
            KafkaAclEntry kafkaAclEntry) {
        policy.require(Action.ADMIN_BROKER_OPS);
        try {
            kafkaAdmin.createAcl(requireKafka(connectionId), toKafkaAclEntry(kafkaAclEntry));
            return ResponseEntity.ok(Map.of("status", "created"));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
        }
    }

    @Override
    public ResponseEntity<Map<String, String>> kafkaDeleteAcl(
            String connectionId,
            String resourceType,
            String resourceName,
            String principal,
            KafkaAclEntry kafkaAclEntry) {
        policy.require(Action.ADMIN_BROKER_OPS);
        try {
            kafkaAdmin.deleteAcl(requireKafka(connectionId), toKafkaAclEntry(kafkaAclEntry));
            return ResponseEntity.ok(Map.of("status", "deleted"));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
        }
    }

    @Override
    public ResponseEntity<Map<String, String>> kafkaReplaceAcl(
            String connectionId,
            String resourceType,
            String resourceName,
            String principal,
            KafkaReplaceAclRequest kafkaReplaceAclRequest) {
        policy.require(Action.ADMIN_BROKER_OPS);
        if (kafkaReplaceAclRequest.getOldBinding() == null || kafkaReplaceAclRequest.getNewBinding() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "oldBinding and newBinding required");
        }
        try {
            kafkaAdmin.replaceAcl(requireKafka(connectionId), toReplaceAclRequest(kafkaReplaceAclRequest));
            return ResponseEntity.ok(Map.of("status", "replaced"));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
        }
    }

    private ConnectionProfile requireKafka(String connectionId) {
        ConnectionProfile profile = connectionRegistry
                .find(connectionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (profile.getProtocol() != ProtocolType.KAFKA) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Connection is not Kafka");
        }
        policy.requireProtocol(profile.getProtocol());
        return profile;
    }

    private static HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }

    private static KafkaPublishResult toKafkaPublishResult(KafkaAdminModels.PublishResult r) {
        KafkaPublishResult out = new KafkaPublishResult();
        out.setTopic(r.getTopic());
        out.setPartition(r.getPartition());
        out.setOffset(r.getOffset());
        out.setStatus(r.getStatus());
        return out;
    }

    private static KafkaAdminModels.CreateTopicRequest toCreateTopicRequest(KafkaCreateTopicRequest r) {
        KafkaAdminModels.CreateTopicRequest out = new KafkaAdminModels.CreateTopicRequest();
        out.setName(r.getName());
        out.setPartitions(r.getPartitions() != null ? r.getPartitions() : 1);
        out.setReplicationFactor(r.getReplicationFactor() != null ? r.getReplicationFactor().shortValue() : 1);
        out.setConfigs(r.getConfigs());
        return out;
    }

    private static KafkaAdminModels.FlushTopicRequest toFlushTopicRequest(KafkaFlushTopicRequest r) {
        KafkaAdminModels.FlushTopicRequest out = new KafkaAdminModels.FlushTopicRequest();
        out.setMode(r.getMode());
        out.setPartition(r.getPartition());
        return out;
    }

    private static KafkaAdminModels.AlterTopicConfigsRequest toAlterTopicConfigsRequest(
            KafkaAlterTopicConfigsRequest r) {
        KafkaAdminModels.AlterTopicConfigsRequest out = new KafkaAdminModels.AlterTopicConfigsRequest();
        out.setConfigs(r.getConfigs());
        return out;
    }

    private static KafkaAdminModels.KafkaAclEntry toKafkaAclEntry(KafkaAclEntry e) {
        KafkaAdminModels.KafkaAclEntry out = new KafkaAdminModels.KafkaAclEntry();
        out.setResourceType(e.getResourceType());
        out.setResourceName(e.getResourceName());
        out.setPatternType(e.getPatternType());
        out.setPrincipal(e.getPrincipal());
        out.setHost(e.getHost());
        out.setOperation(e.getOperation());
        out.setPermissionType(e.getPermissionType());
        return out;
    }

    private static KafkaAclEntry toKafkaAclEntry(KafkaAdminModels.KafkaAclEntry e) {
        KafkaAclEntry out = new KafkaAclEntry();
        out.setResourceType(e.getResourceType());
        out.setResourceName(e.getResourceName());
        out.setPatternType(e.getPatternType());
        out.setPrincipal(e.getPrincipal());
        out.setHost(e.getHost());
        out.setOperation(e.getOperation());
        out.setPermissionType(e.getPermissionType());
        return out;
    }

    private static KafkaAdminModels.ReplaceAclRequest toReplaceAclRequest(KafkaReplaceAclRequest r) {
        KafkaAdminModels.ReplaceAclRequest out = new KafkaAdminModels.ReplaceAclRequest();
        out.setOldBinding(toKafkaAclEntry(r.getOldBinding()));
        out.setNewBinding(toKafkaAclEntry(r.getNewBinding()));
        return out;
    }
}
