package com.eventore.api.delegate;

import com.eventore.api.generated.inspect.InspectApiDelegate;
import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.UnifiedMessage;
import com.eventore.inspect.InspectorRegistry;
import com.eventore.inspect.domain.InspectModels.ClusterInfo;
import com.eventore.inspect.domain.InspectModels.ConsumerGroupDetail;
import com.eventore.inspect.domain.InspectModels.ConsumerGroupSummary;
import com.eventore.inspect.domain.InspectModels.GroupOffset;
import com.eventore.inspect.domain.InspectModels.MessageSearchRequest;
import com.eventore.inspect.domain.InspectModels.ProtocolInspectCapabilities;
import com.eventore.inspect.domain.InspectModels.TopicDetail;
import com.eventore.inspect.spi.MessagingInspector;
import com.eventore.security.Action;
import com.eventore.security.DeploymentModePolicy;
import com.eventore.service.AuditService;
import com.eventore.service.ConnectionRegistry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InspectApiDelegateImpl implements InspectApiDelegate {

    private final ConnectionRegistry connectionRegistry;
    private final InspectorRegistry inspectorRegistry;
    private final DeploymentModePolicy policy;
    private final AuditService auditService;

    public InspectApiDelegateImpl(
            ConnectionRegistry connectionRegistry,
            InspectorRegistry inspectorRegistry,
            DeploymentModePolicy policy,
            AuditService auditService) {
        this.connectionRegistry = connectionRegistry;
        this.inspectorRegistry = inspectorRegistry;
        this.policy = policy;
        this.auditService = auditService;
    }

    @Override
    public ResponseEntity<ProtocolInspectCapabilities> inspectCapabilities(String connectionId) {
        policy.require(Action.BROWSE_DESTINATIONS);
        return ResponseEntity.ok(inspector(profile(connectionId)).capabilities());
    }

    @Override
    public ResponseEntity<ClusterInfo> inspectCluster(String connectionId) {
        policy.require(Action.BROWSE_DESTINATIONS);
        ConnectionProfile profile = profile(connectionId);
        return ResponseEntity.ok(inspector(profile).clusterInfo(profile));
    }

    @Override
    public ResponseEntity<Object> inspectBrokers(String connectionId) {
        policy.require(Action.BROWSE_DESTINATIONS);
        ConnectionProfile profile = profile(connectionId);
        MessagingInspector insp = inspector(profile);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cluster", insp.clusterInfo(profile));
        result.put("brokerInfo", insp.brokerInfo(profile));
        return ResponseEntity.ok(result);
    }

    @Override
    public ResponseEntity<List<ConsumerGroupSummary>> listConsumerGroups(String connectionId) {
        policy.require(Action.BROWSE_DESTINATIONS);
        ConnectionProfile profile = profile(connectionId);
        return ResponseEntity.ok(inspector(profile).listConsumerGroups(profile));
    }

    @Override
    public ResponseEntity<ConsumerGroupDetail> describeConsumerGroup(String connectionId, String groupId) {
        policy.require(Action.BROWSE_DESTINATIONS);
        ConnectionProfile profile = profile(connectionId);
        return ResponseEntity.ok(inspector(profile).describeConsumerGroup(profile, groupId));
    }

    @Override
    public ResponseEntity<List<TopicDetail>> listInspectTopics(String connectionId, String filter) {
        policy.require(Action.BROWSE_DESTINATIONS);
        ConnectionProfile profile = profile(connectionId);
        return ResponseEntity.ok(inspector(profile).listTopics(profile, filter));
    }

    @Override
    public ResponseEntity<TopicDetail> describeInspectTopic(String connectionId, String topic) {
        policy.require(Action.BROWSE_DESTINATIONS);
        ConnectionProfile profile = profile(connectionId);
        return ResponseEntity.ok(inspector(profile).describeTopic(profile, topic));
    }

    @Override
    public ResponseEntity<List<GroupOffset>> inspectLag(String connectionId, String groupId, String topic) {
        policy.require(Action.BROWSE_DESTINATIONS);
        if (groupId == null || groupId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "groupId is required for lag");
        }
        ConnectionProfile profile = profile(connectionId);
        return ResponseEntity.ok(inspector(profile).consumerLag(profile, groupId, topic));
    }

    @Override
    public ResponseEntity<List<UnifiedMessage>> searchMessages(
            String connectionId, MessageSearchRequest messageSearchRequest) {
        policy.require(Action.BROWSE_DESTINATIONS);
        ConnectionProfile profile = profile(connectionId);
        int maxMessages = messageSearchRequest != null && messageSearchRequest.getMaxMessages() != null
                ? messageSearchRequest.getMaxMessages()
                : 0;
        auditService.inspectSearch(
                connectionId,
                profile.getProtocol(),
                messageSearchRequest != null ? messageSearchRequest.getTopic() : null,
                maxMessages);
        return ResponseEntity.ok(inspector(profile).searchMessages(profile, messageSearchRequest));
    }

    private ConnectionProfile profile(String connectionId) {
        return connectionRegistry
                .find(connectionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private MessagingInspector inspector(ConnectionProfile profile) {
        policy.requireProtocol(profile.getProtocol());
        return inspectorRegistry.get(profile.getProtocol());
    }
}
