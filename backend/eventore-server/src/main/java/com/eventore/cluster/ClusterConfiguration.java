package com.eventore.cluster;

import com.eventore.config.EventoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration providing the cluster SubscriptionDistributionBus bean.
 */
@Configuration
public class ClusterConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ClusterConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public SubscriptionDistributionBus subscriptionDistributionBus(
            EventoreProperties properties,
            ObjectMapper objectMapper) {
        EventoreProperties.Cluster cluster = properties.getCluster();
        if (cluster.getMode() == EventoreProperties.Cluster.Mode.REDIS) {
            log.info("Initializing Distributed Redis SubscriptionDistributionBus (node={}, channel={})",
                    cluster.getNodeId(), cluster.getChannel());
            return new RedisSubscriptionDistributionBus(
                    cluster.getNodeId(),
                    cluster.getChannel(),
                    objectMapper);
        }

        log.info("Initializing Local Standalone SubscriptionDistributionBus (node={})", cluster.getNodeId());
        return new LocalSubscriptionDistributionBus(cluster.getNodeId());
    }
}
