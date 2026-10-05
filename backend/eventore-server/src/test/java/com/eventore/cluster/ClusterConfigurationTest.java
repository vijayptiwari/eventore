package com.eventore.cluster;

import com.eventore.config.EventoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClusterConfigurationTest {
    @Test
    void rejectsRedisModeUntilNetworkTransportExists() {
        EventoreProperties properties = new EventoreProperties();
        properties.getCluster().setMode(EventoreProperties.Cluster.Mode.REDIS);
        assertThatThrownBy(() -> new ClusterConfiguration()
                .subscriptionDistributionBus(properties, new ObjectMapper()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REDIS cluster mode is not implemented");
    }
}
