package com.eventore.connector.kinesis;

import com.eventore.connector.cloud.CloudClientSupport;
import com.eventore.connector.spi.MessageHandler;
import com.eventore.connector.spi.MessagingConnector;
import com.eventore.connector.spi.PayloadCodec;
import com.eventore.connector.spi.PublishRequest;
import com.eventore.connector.spi.SubscribeDestinations;
import com.eventore.connector.spi.SubscribeRequest;
import com.eventore.connector.spi.SubscriptionKeys;
import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.MessageDirection;
import com.eventore.domain.ProtocolType;
import com.eventore.domain.TopicRef;
import com.eventore.domain.UnifiedMessage;
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
import software.amazon.awssdk.services.kinesis.model.ExpiredIteratorException;
import software.amazon.awssdk.services.kinesis.model.GetRecordsRequest;
import software.amazon.awssdk.services.kinesis.model.GetRecordsResponse;
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorRequest;
import software.amazon.awssdk.services.kinesis.model.ListShardsRequest;
import software.amazon.awssdk.services.kinesis.model.PutRecordRequest;
import software.amazon.awssdk.services.kinesis.model.Record;
import software.amazon.awssdk.services.kinesis.model.Shard;
import software.amazon.awssdk.services.kinesis.model.ShardIteratorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class KinesisMessagingConnector implements MessagingConnector {

    private static final Logger log = LoggerFactory.getLogger(KinesisMessagingConnector.class);

    private static final long POLL_INTERVAL_MS = 500;
    private static final long MAX_BACKOFF_MS = 30_000;

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
            List<Shard> shards = listAllShards(kinesis, stream);
            Map<String, String> iterators = new ConcurrentHashMap<>();
            for (Shard shard : shards) {
                iterators.put(shard.shardId(), latestIterator(kinesis, stream, shard.shardId()));
            }
            long backoffMs = POLL_INTERVAL_MS;
            while (running.get()) {
                try {
                    for (Map.Entry<String, String> entry : iterators.entrySet()) {
                        if (entry.getValue() == null) {
                            continue;
                        }
                        GetRecordsResponse records;
                        try {
                            records = kinesis.getRecords(GetRecordsRequest.builder()
                                    .shardIterator(entry.getValue())
                                    .limit(100)
                                    .build());
                        } catch (ExpiredIteratorException expired) {
                            // Shard iterators expire after 5 minutes; fetch a fresh
                            // one and resume tailing the shard.
                            log.debug("Kinesis shard iterator expired for stream {} shard {}; refreshing",
                                    stream, entry.getKey(), expired);
                            entry.setValue(latestIterator(kinesis, stream, entry.getKey()));
                            continue;
                        }
                        entry.setValue(records.nextShardIterator());
                        for (Record record : records.records()) {
                            UnifiedMessage msg = new UnifiedMessage();
                            msg.setConnectionId(profile.getId());
                            msg.setProtocol(ProtocolType.KINESIS);
                            msg.setDestination(stream);
                            msg.setDirection(MessageDirection.INBOUND);
                            PayloadCodec.Decoded decoded = PayloadCodec.fromBytes(record.data().asByteArray());
                            msg.setPayload(decoded.text());
                            msg.setContentType(decoded.contentType());
                            msg.putHeader("shardId", entry.getKey());
                            msg.putHeader("sequenceNumber", record.sequenceNumber());
                            msg.putHeader("partitionKey", record.partitionKey());
                            handler.onMessage(msg);
                        }
                    }
                    backoffMs = POLL_INTERVAL_MS;
                    Thread.sleep(POLL_INTERVAL_MS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    if (!running.get()) {
                        return;
                    }
                    // Back off exponentially on repeated failures instead of hot-spinning;
                    // the delay resets after the next successful poll.
                    log.warn("Kinesis poll failed for connection {} stream {}; retrying in {} ms",
                            profile.getId(), stream, backoffMs, e);
                    handler.onError(e.getMessage());
                    Thread.sleep(backoffMs);
                    backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
                }
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (running.get()) {
                log.warn("Kinesis poll loop failed for connection {} stream {}", profile.getId(), stream, e);
                handler.onError(e.getMessage());
            }
        }
    }

    private static String latestIterator(KinesisClient kinesis, String stream, String shardId) {
        return kinesis.getShardIterator(GetShardIteratorRequest.builder()
                        .streamName(stream)
                        .shardId(shardId)
                        .shardIteratorType(ShardIteratorType.LATEST)
                        .build())
                .shardIterator();
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
                    .data(SdkBytes.fromByteArray(
                            PayloadCodec.toBytes(request.getPayload(), request.getContentType())))
                    .build());
        } catch (Exception e) {
            log.warn("Kinesis publish failed for connection {} stream {}",
                    profile.getId(), request.getDestination(), e);
            throw new IllegalStateException("Kinesis publish failed: " + e.getMessage(), e);
        }
    }

    private static List<Shard> listAllShards(KinesisClient kinesis, String stream) {
        List<Shard> shards = new ArrayList<>();
        String nextToken = null;
        do {
            ListShardsRequest.Builder builder = ListShardsRequest.builder();
            if (nextToken == null) {
                builder.streamName(stream);
            } else {
                builder.nextToken(nextToken);
            }
            var response = kinesis.listShards(builder.build());
            shards.addAll(response.shards());
            nextToken = response.nextToken();
        } while (nextToken != null);
        return shards;
    }

    @Override
    public void close(String connectionId) {
        active.entrySet().removeIf(e -> {
            if (SubscriptionKeys.belongsToConnection(e.getKey(), connectionId)) {
                try {
                    e.getValue().close();
                } catch (Exception ex) {
                    log.debug("Error closing Kinesis subscription {} while closing connection {}",
                            e.getKey(), connectionId, ex);
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
            } catch (Exception e) {
                log.debug("Error closing previous Kinesis subscription for key {}", key, e);
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
