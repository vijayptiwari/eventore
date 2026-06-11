package com.eventore.service;

import com.eventore.config.EventoreProperties;
import com.eventore.connector.ConnectorRegistry;
import com.eventore.connector.spi.MessageHandler;
import com.eventore.connector.spi.MessagingConnector;
import com.eventore.connector.spi.SubscribeRequest;
import com.eventore.domain.ConnectionProfile;
import com.eventore.domain.UnifiedMessage;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SubscriptionManager {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionManager.class);

    private final ConnectorRegistry connectorRegistry;
    private final EventoreProperties properties;
    private final MetricsService metricsService;
    private final Map<String, ActiveSubscription> subscriptions = new ConcurrentHashMap<>();
    /** Per-connection locks so connector I/O on one connection never blocks others. */
    private final Map<String, Object> connectionLocks = new ConcurrentHashMap<>();
    /** Atomic reservation counter keeping the max-concurrent cap exact across connections. */
    private final AtomicInteger subscriptionCount = new AtomicInteger();

    public SubscriptionManager(
            ConnectorRegistry connectorRegistry,
            EventoreProperties properties,
            MetricsService metricsService) {
        this.connectorRegistry = connectorRegistry;
        this.properties = properties;
        this.metricsService = metricsService;
    }

    public String subscribe(
            ConnectionProfile profile,
            SubscribeRequest request,
            Consumer<StreamEvent> eventConsumer,
            boolean useQueue) {
        if (subscriptionCount.incrementAndGet() > properties.getSubscriptions().getMaxConcurrent()) {
            subscriptionCount.decrementAndGet();
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS, "Maximum concurrent subscriptions reached");
        }
        boolean reserved = true;
        try {
            synchronized (lockFor(profile.getId())) {
                String subscriptionId = UUID.randomUUID().toString();
                request.setSubscriptionKey(subscriptionId);
                int capacity = properties.getSubscriptions().getQueueCapacity();
                BlockingQueue<StreamEvent> queue = useQueue ? new LinkedBlockingQueue<>(capacity) : null;
                MessagingConnector connector = connectorRegistry.get(profile.getProtocol());

                MessageHandler handler = new MessageHandler() {
                    @Override
                    public void onMessage(UnifiedMessage message) {
                        metricsService.recordMessage(profile.getProtocol());
                        StreamEvent event = StreamEvent.message(subscriptionId, message);
                        if (queue != null) {
                            if (!queue.offer(event)) {
                                queue.poll();
                                while (!queue.offer(event)) {
                                    queue.poll();
                                }
                                eventConsumer.accept(StreamEvent.slowConsumer(subscriptionId));
                            }
                        }
                        eventConsumer.accept(event);
                    }

                    @Override
                    public void onError(String message) {
                        eventConsumer.accept(StreamEvent.error(subscriptionId, message));
                    }
                };

                try {
                    AutoCloseable closeable = connector.subscribe(profile, request, handler);
                    ActiveSubscription active =
                            new ActiveSubscription(subscriptionId, profile, request, closeable, queue);
                    subscriptions.put(subscriptionId, active);
                    reserved = false;
                    metricsService.incrementSubscriptions();
                    return subscriptionId;
                } catch (Exception e) {
                    log.error(
                            "Subscribe failed for connection {} destination {}",
                            profile.getId(),
                            request.getDestination(),
                            e);
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Subscribe failed: " + e.getMessage(), e);
                }
            }
        } finally {
            if (reserved) {
                subscriptionCount.decrementAndGet();
            }
        }
    }

    public void unsubscribe(String subscriptionId) {
        ActiveSubscription active = subscriptions.remove(subscriptionId);
        if (active != null) {
            synchronized (lockFor(active.profile().getId())) {
                active.close();
            }
            subscriptionCount.decrementAndGet();
            metricsService.decrementSubscriptions();
        }
    }

    private Object lockFor(String connectionId) {
        return connectionLocks.computeIfAbsent(connectionId, k -> new Object());
    }

    public boolean ownsSubscription(String connectionId, String subscriptionId) {
        ActiveSubscription active = subscriptions.get(subscriptionId);
        return active != null && active.profile().getId().equals(connectionId);
    }

    public BlockingQueue<StreamEvent> queue(String subscriptionId) {
        ActiveSubscription active = subscriptions.get(subscriptionId);
        if (active == null || active.queue() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found");
        }
        return active.queue();
    }

    public int activeCount() {
        return subscriptions.size();
    }

    public void closeAllForConnection(String connectionId) {
        synchronized (lockFor(connectionId)) {
            subscriptions.entrySet().removeIf(entry -> {
                if (entry.getValue().profile().getId().equals(connectionId)) {
                    entry.getValue().close();
                    subscriptionCount.decrementAndGet();
                    metricsService.decrementSubscriptions();
                    return true;
                }
                return false;
            });
        }
        connectionLocks.remove(connectionId);
    }

    public record StreamEvent(String type, String subscriptionId, UnifiedMessage message, String detail) {
        public static StreamEvent message(String subscriptionId, UnifiedMessage message) {
            return new StreamEvent("MESSAGE", subscriptionId, message, null);
        }

        public static StreamEvent slowConsumer(String subscriptionId) {
            return new StreamEvent("SLOW_CONSUMER", subscriptionId, null, "Consumer queue full; dropping oldest");
        }

        public static StreamEvent error(String subscriptionId, String detail) {
            return new StreamEvent("ERROR", subscriptionId, null, detail);
        }

        public static StreamEvent heartbeat(String subscriptionId) {
            return new StreamEvent("HEARTBEAT", subscriptionId, null, null);
        }
    }

    private record ActiveSubscription(
            String id,
            ConnectionProfile profile,
            SubscribeRequest request,
            AutoCloseable closeable,
            BlockingQueue<StreamEvent> queue) {

        void close() {
            try {
                closeable.close();
            } catch (Exception e) {
                log.debug("Error closing subscription {} for connection {}", id, profile.getId(), e);
            }
        }
    }
}
