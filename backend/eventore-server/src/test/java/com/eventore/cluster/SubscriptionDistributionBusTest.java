package com.eventore.cluster;

import com.eventore.domain.UnifiedMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionDistributionBusTest {

    @Test
    void localBusDispatchesToSubscribersAndTracksMetrics() {
        LocalSubscriptionDistributionBus bus = new LocalSubscriptionDistributionBus("node-alpha");
        String subId = "sub-101";

        List<ClusterStreamFrame> received = new ArrayList<>();
        bus.subscribe(subId, received::add);

        ClusterStatusDto statusInitial = bus.getStatus();
        assertThat(statusInitial.mode()).isEqualTo("LOCAL");
        assertThat(statusInitial.nodeId()).isEqualTo("node-alpha");
        assertThat(statusInitial.activeSubscribers()).isEqualTo(1);
        assertThat(statusInitial.totalBroadcasts()).isZero();

        UnifiedMessage msg = new UnifiedMessage();
        msg.setId("msg-1");
        msg.setDestination("topic-a");
        msg.setPayload("{\"hello\":\"world\"}");
        msg.setTimestamp(Instant.now());
        msg.setHeaders(Map.of("traceId", "tr-123"));

        ClusterStreamFrame frame = ClusterStreamFrame.of(subId, "node-alpha", "MESSAGE", msg, null);
        bus.publish(frame);

        assertThat(received).hasSize(1);
        assertThat(received.get(0).subscriptionId()).isEqualTo(subId);
        assertThat(received.get(0).message().getId()).isEqualTo("msg-1");

        ClusterStatusDto statusAfter = bus.getStatus();
        assertThat(statusAfter.totalBroadcasts()).isEqualTo(1);
        assertThat(statusAfter.totalReceived()).isEqualTo(1);

        bus.unsubscribe(subId);
        assertThat(bus.getStatus().activeSubscribers()).isZero();
    }

    @Test
    void redisBusSerializesAndDispatchesMessagesWithPeerAwareness() {
        ObjectMapper mapper = new ObjectMapper();
        RedisSubscriptionDistributionBus bus = new RedisSubscriptionDistributionBus("node-beta", "test:channel", mapper);
        String subId = "sub-202";

        AtomicReference<ClusterStreamFrame> received = new AtomicReference<>();
        bus.subscribe(subId, received::set);

        ClusterStatusDto initialStatus = bus.getStatus();
        assertThat(initialStatus.mode()).isEqualTo("REDIS");
        assertThat(initialStatus.nodeId()).isEqualTo("node-beta");
        assertThat(initialStatus.healthy()).isTrue();

        UnifiedMessage msg = new UnifiedMessage();
        msg.setId("msg-99");
        msg.setDestination("orders");
        msg.setPayload("{\"orderId\":99}");
        msg.setTimestamp(Instant.now());

        ClusterStreamFrame frame = ClusterStreamFrame.of(subId, "node-gamma", "MESSAGE", msg, null);
        bus.publish(frame);

        assertThat(received.get()).isNotNull();
        assertThat(received.get().subscriptionId()).isEqualTo(subId);
        assertThat(received.get().message().getId()).isEqualTo("msg-99");

        // Simulate incoming message from peer node over Redis PubSub
        bus.registerPeer("node-gamma");
        bus.registerPeer("node-delta");
        assertThat(bus.getStatus().connectedPeers()).isEqualTo(3);
    }
}
