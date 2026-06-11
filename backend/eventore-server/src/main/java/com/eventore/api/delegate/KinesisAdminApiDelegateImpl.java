package com.eventore.api.delegate;

import com.eventore.api.generated.kinesis.KinesisAdminApiDelegate;
import com.eventore.api.generated.kinesis.model.KinesisListShards200ResponseInner;
import com.eventore.connector.cloud.CloudClientSupport;
import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.ProtocolType;
import com.eventore.security.Action;
import com.eventore.security.DeploymentModePolicy;
import com.eventore.service.ConnectionRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.ListShardsRequest;
import software.amazon.awssdk.services.kinesis.model.ListShardsResponse;
import software.amazon.awssdk.services.kinesis.model.Shard;

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
        ConnectionProfile profile = requireKinesis(connectionId);
        policy.require(Action.BROWSE_DESTINATIONS);
        if (streamName == null || streamName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "streamName is required");
        }
        try (KinesisClient kinesis = KinesisClient.builder()
                .region(CloudClientSupport.awsRegion(profile))
                .credentialsProvider(CloudClientSupport.awsCredentials(profile))
                .build()) {
            List<KinesisListShards200ResponseInner> shards = new ArrayList<>();
            String nextToken = null;
            do {
                ListShardsRequest.Builder builder = ListShardsRequest.builder();
                if (nextToken == null) {
                    builder.streamName(streamName);
                } else {
                    builder.nextToken(nextToken);
                }
                ListShardsResponse response = kinesis.listShards(builder.build());
                for (Shard shard : response.shards()) {
                    KinesisListShards200ResponseInner item = new KinesisListShards200ResponseInner();
                    item.setShardId(shard.shardId());
                    if (shard.hashKeyRange() != null) {
                        item.setHashKeyRange(shard.hashKeyRange().startingHashKey()
                                + "-"
                                + shard.hashKeyRange().endingHashKey());
                    }
                    if (shard.sequenceNumberRange() != null) {
                        String ending = shard.sequenceNumberRange().endingSequenceNumber();
                        item.setSequenceNumberRange(shard.sequenceNumberRange().startingSequenceNumber()
                                + "-"
                                + (ending != null ? ending : "open"));
                    }
                    shards.add(item);
                }
                nextToken = response.nextToken();
            } while (nextToken != null);
            return ResponseEntity.ok(shards);
        } catch (software.amazon.awssdk.services.kinesis.model.ResourceNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Stream not found: " + streamName);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Kinesis listShards failed: " + e.getMessage());
        }
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
