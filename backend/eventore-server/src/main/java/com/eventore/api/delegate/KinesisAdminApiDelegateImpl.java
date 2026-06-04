package com.eventore.api.delegate;

import com.eventore.api.generated.kinesis.KinesisAdminApiDelegate;
import com.eventore.api.generated.kinesis.model.KinesisListShards200ResponseInner;
import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.ProtocolType;
import com.eventore.security.DeploymentModePolicy;
import com.eventore.service.ConnectionRegistry;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnClass(name = "com.eventore.connector.kinesis.KinesisMessagingConnector")
public class KinesisAdminApiDelegateImpl implements KinesisAdminApiDelegate {

    private final ConnectionRegistry connectionRegistry;
    private final DeploymentModePolicy policy;

    public KinesisAdminApiDelegateImpl(ConnectionRegistry connectionRegistry, DeploymentModePolicy policy) {
        this.connectionRegistry = connectionRegistry;
        this.policy = policy;
    }

    @Override
    public ResponseEntity<Object> kinesisCapabilities(String connectionId) {
        requireKinesis(connectionId);
        return ResponseEntity.ok(Map.of(
                "features",
                List.of("listShards", "inspectViaGenericApi")));
    }

    @Override
    public ResponseEntity<List<KinesisListShards200ResponseInner>> kinesisListShards(
            String connectionId, String streamName) {
        requireKinesis(connectionId);
        throw new ResponseStatusException(
                HttpStatus.NOT_IMPLEMENTED,
                "Kinesis shard listing will be implemented in eventore-provider-kinesis");
    }

    private ConnectionProfile requireKinesis(String connectionId) {
        ConnectionProfile profile = connectionRegistry
                .find(connectionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (profile.getProtocol() != ProtocolType.KINESIS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Connection is not Kinesis");
        }
        policy.requireProtocol(profile.getProtocol());
        return profile;
    }
}
