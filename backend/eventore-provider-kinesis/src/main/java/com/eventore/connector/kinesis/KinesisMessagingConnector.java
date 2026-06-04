package com.eventore.connector.kinesis;

import com.eventore.connector.cloud.CloudClientSupport;
import com.eventore.connector.spi.MessageHandler;
import com.eventore.connector.spi.MessagingConnector;
import com.eventore.connector.spi.PublishRequest;
import com.eventore.connector.spi.SubscribeDestinations;
import com.eventore.connector.spi.SubscribeRequest;
import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.MessageDirection;
import com.eventore.domain.ProtocolType;
import com.eventore.domain.TopicRef;
import com.eventore.domain.UnifiedMessage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.GetRecordsRequest;
import software.amazon.awssdk.services.kinesis.model.GetRecordsResponse;
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorRequest;
import software.amazon.awssdk.services.kinesis.model.ListShardsRequest;
import software.amazon.awssdk.services.kinesis.model.PutRecordRequest;
import software.amazon.awssdk.services.kinesis.model.Record;
import software.amazon.awssdk.services.kinesis.model.Shard;
import software.amazon.awssdk.services.kinesis.model.ShardIteratorType;
import org.springframework.stereotype.Component;

@Component
public class KinesisMessagingConnector implements MessagingConnector {

    private final Map<String, AutoCloseable> active = new ConcurrentHashMap<>();

    @Override
    public ProtocolType protocol() {
        return ProtocolType.KINESIS;
    }

    @Override
    public void validate(ConnectionProfile profile) {
        try (KinesisClient client = client(profile)) {
            client.listStreams();
        } catch (Exception e) {
            throw new IllegalStateException("Kinesis connection failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<TopicRef> listDestinations(ConnectionProfile profile) {
        List<TopicRef> refs = new ArrayList<>();
        try (KinesisClient kinesis = client(profile)) {
            kinesis.listStreams().streamNames().forEach(name -> refs.add(new TopicRef(name, "stream", ProtocolType.KINESIS)));
        } catch (Exception e) {
            throw new IllegalStateException("List Kinesis streams failed: " + e.getMessage(), e);
        }
        return refs;
    }

    @Override
    public AutoCloseable subscribe(ConnectionProfile profile, SubscribeRequest request, MessageHandler handler) {
        String stream = SubscribeDestinations.resolve(request).get(0);
        String key = request.getSubscriptionKey() != null
                ? request.getSubscriptionKey()
                : profile.getId() + ":" + stream;
        closeExisting(key);
        KinesisClient kinesis = client(profile);
        AtomicBoolean running = new AtomicBoolean(true);
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "kinesis-sub-" + key);
            t.setDaemon(true);
            return t;
        });
        executor.submit(() -> pollStream(profile, handler, kinesis, stream, running));
        AutoCloseable closeable = () -> {
            running.set(false);
            executor.shutdownNow();
            kinesis.close();
            active.remove(key);
        };
        active.put(key, closeable);
        return closeable;
    }

    private void pollStream(
            ConnectionProfile profile,
            MessageHandler handler,
            KinesisClient kinesis,
            String stream,
            AtomicBoolean running) {
        try {
            List<Shard> shards =
                    kinesis.listShards(ListShardsRequest.builder().streamName(stream).build()).shards();
            Map<String, String> iterators = new ConcurrentHashMap<>();
            for (Shard shard : shards) {
                String it = kinesis.getShardIterator(GetShardIteratorRequest.builder()
                                .streamName(stream)
                                .shardId(shard.shardId())
                                .shardIteratorType(ShardIteratorType.LATEST)
                                .build())
                        .shardIterator();
                iterators.put(shard.shardId(), it);
            }
            while (running.get()) {
                for (Map.Entry<String, String> entry : iterators.entrySet()) {
                    if (entry.getValue() == null) {
                        continue;
                    }
                    GetRecordsResponse records = kinesis.getRecords(
                            GetRecordsRequest.builder().shardIterator(entry.getValue()).limit(100).build());
                    entry.setValue(records.nextShardIterator());
                    for (Record record : records.records()) {
                        UnifiedMessage msg = new UnifiedMessage();
                        msg.setConnectionId(profile.getId());
                        msg.setProtocol(ProtocolType.KINESIS);
                        msg.setDestination(stream);
                        msg.setDirection(MessageDirection.INBOUND);
                        msg.setPayload(new String(record.data().asByteArray(), StandardCharsets.UTF_8));
                        msg.getHeaders().put("shardId", entry.getKey());
                        msg.getHeaders().put("sequenceNumber", record.sequenceNumber());
                        msg.getHeaders().put("partitionKey", record.partitionKey());
                        handler.onMessage(msg);
                    }
                }
                Thread.sleep(500);
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (running.get()) {
                handler.onError(e.getMessage());
            }
        }
    }

    @Override
    public void publish(ConnectionProfile profile, PublishRequest request) {
        try (KinesisClient kinesis = client(profile)) {
            String key = request.getHeaders() != null && request.getHeaders().get("key") != null
                    ? request.getHeaders().get("key")
                    : UUID.randomUUID().toString();
            kinesis.putRecord(PutRecordRequest.builder()
                    .streamName(request.getDestination())
                    .partitionKey(key)
                    .data(SdkBytes.fromString(
                            request.getPayload() != null ? request.getPayload() : "",
                            StandardCharsets.UTF_8))
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("Kinesis publish failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void close(String connectionId) {
        active.entrySet().removeIf(e -> {
            if (e.getKey().startsWith(connectionId)) {
                try {
                    e.getValue().close();
                } catch (Exception ignored) {
                    // ignore
                }
                return true;
            }
            return false;
        });
    }

    private void closeExisting(String key) {
        AutoCloseable existing = active.remove(key);
        if (existing != null) {
            try {
                existing.close();
            } catch (Exception ignored) {
                // ignore
            }
        }
    }

    private KinesisClient client(ConnectionProfile profile) {
        return KinesisClient.builder()
                .region(CloudClientSupport.awsRegion(profile))
                .credentialsProvider(CloudClientSupport.awsCredentials(profile))
                .build();
    }
}
