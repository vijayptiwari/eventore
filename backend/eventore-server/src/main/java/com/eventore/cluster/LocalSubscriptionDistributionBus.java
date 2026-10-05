package com.eventore.cluster;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Local standalone implementation of {@link SubscriptionDistributionBus}.
 * Dispatches stream frames in-memory with zero external infrastructure dependencies.
 */
public class LocalSubscriptionDistributionBus implements SubscriptionDistributionBus {

    private static final Logger log = LoggerFactory.getLogger(LocalSubscriptionDistributionBus.class);

    private final String nodeId;
    private final Map<String, Set<Consumer<ClusterStreamFrame>>> listeners = new ConcurrentHashMap<>();
    private final AtomicLong totalBroadcasts = new AtomicLong();
    private final AtomicLong totalReceived = new AtomicLong();

    public LocalSubscriptionDistributionBus(String nodeId) {
        this.nodeId = nodeId != null && !nodeId.isBlank() ? nodeId : "local-node";
    }

    @Override
    public void publish(ClusterStreamFrame frame) {
        if (frame == null || frame.subscriptionId() == null) {
            return;
        }
        totalBroadcasts.incrementAndGet();
        Set<Consumer<ClusterStreamFrame>> subs = listeners.get(frame.subscriptionId());
        if (subs != null) {
            for (Consumer<ClusterStreamFrame> listener : subs) {
                try {
                    totalReceived.incrementAndGet();
                    listener.accept(frame);
                } catch (Exception e) {
                    log.warn("Error dispatching cluster frame {} to local listener: {}", frame.frameId(), e.getMessage());
                }
            }
        }
    }

    @Override
    public void subscribe(String subscriptionId, Consumer<ClusterStreamFrame> listener) {
        if (subscriptionId == null || listener == null) {
            return;
        }
        listeners.computeIfAbsent(subscriptionId, k -> new CopyOnWriteArraySet<>()).add(listener);
    }

    @Override
    public void unsubscribe(String subscriptionId) {
        if (subscriptionId != null) {
            listeners.remove(subscriptionId);
        }
    }

    @Override
    public ClusterStatusDto getStatus() {
        int activeSubCount = listeners.values().stream().mapToInt(Set::size).sum();
        return new ClusterStatusDto(
                "LOCAL",
                nodeId,
                activeSubCount,
                totalBroadcasts.get(),
                totalReceived.get(),
                1,
                true);
    }
}
