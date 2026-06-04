package com.eventore.api.delegate;

import com.eventore.api.generated.inspect.InspectApiDelegate;
import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.UnifiedMessage;
import com.eventore.inspect.InspectorRegistry;
import com.eventore.inspect.domain.InspectModels.MessageSearchRequest;
import com.eventore.inspect.spi.MessagingInspector;
import com.eventore.security.Action;
import com.eventore.security.DeploymentModePolicy;
import com.eventore.service.ConnectionRegistry;
import java.util.ArrayList;
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

    public InspectApiDelegateImpl(
            ConnectionRegistry connectionRegistry,
            InspectorRegistry inspectorRegistry,
            DeploymentModePolicy policy) {
        this.connectionRegistry = connectionRegistry;
        this.inspectorRegistry = inspectorRegistry;
        this.policy = policy;
    }

    @Override
    public ResponseEntity<Object> inspectCapabilities(String connectionId) {
        return ResponseEntity.ok(inspector(connectionId).capabilities());
    }

    @Override
    public ResponseEntity<Object> inspectCluster(String connectionId) {
        policy.require(Action.BROWSE_DESTINATIONS);
        return ResponseEntity.ok(inspector(connectionId).clusterInfo(profile(connectionId)));
    }

    @Override
    public ResponseEntity<Object> inspectBrokers(String connectionId) {
        policy.require(Action.BROWSE_DESTINATIONS);
        MessagingInspector insp = inspector(connectionId);
        Map<String, Object> result = new LinkedHashMap<>();
        ConnectionProfile profile = profile(connectionId);
        result.put("cluster", insp.clusterInfo(profile));
        result.put("brokerInfo", insp.brokerInfo(profile));
        return ResponseEntity.ok(result);
    }

    @Override
    public ResponseEntity<List<Object>> listConsumerGroups(String connectionId) {
        policy.require(Action.BROWSE_DESTINATIONS);
        return ResponseEntity.ok(toObjectList(inspector(connectionId)
                .listConsumerGroups(profile(connectionId))));
    }

    @Override
    public ResponseEntity<Object> describeConsumerGroup(String connectionId, String groupId) {
        policy.require(Action.BROWSE_DESTINATIONS);
        return ResponseEntity.ok(inspector(connectionId)
                .describeConsumerGroup(profile(connectionId), groupId));
    }

    @Override
    public ResponseEntity<List<Object>> listInspectTopics(String connectionId, String filter) {
        policy.require(Action.BROWSE_DESTINATIONS);
        return ResponseEntity.ok(toObjectList(inspector(connectionId)
                .listTopics(profile(connectionId), filter)));
    }

    @Override
    public ResponseEntity<Object> describeInspectTopic(String connectionId, String topic) {
        policy.require(Action.BROWSE_DESTINATIONS);
        return ResponseEntity.ok(inspector(connectionId).describeTopic(profile(connectionId), topic));
    }

    @Override
    public ResponseEntity<List<Object>> inspectLag(String connectionId, String groupId, String topic) {
        policy.require(Action.BROWSE_DESTINATIONS);
        if (groupId == null || groupId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "groupId is required for lag");
        }
        return ResponseEntity.ok(toObjectList(inspector(connectionId)
                .consumerLag(profile(connectionId), groupId, topic)));
    }

    @Override
    public ResponseEntity<List<UnifiedMessage>> searchMessages(
            String connectionId, MessageSearchRequest messageSearchRequest) {
        policy.require(Action.BROWSE_DESTINATIONS);
        return ResponseEntity.ok(inspector(connectionId)
                .searchMessages(profile(connectionId), messageSearchRequest));
    }

    private ConnectionProfile profile(String connectionId) {
        return connectionRegistry
                .find(connectionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @SuppressWarnings("unchecked")
    private static List<Object> toObjectList(List<?> source) {
        return (List<Object>) (List<?>) source;
    }

    private MessagingInspector inspector(String connectionId) {
        ConnectionProfile profile = profile(connectionId);
        policy.requireProtocol(profile.getProtocol());
        return inspectorRegistry.get(profile.getProtocol());
    }
}
