package com.eventore.cluster;

import java.util.function.Consumer;

/**
 * Service Provider Interface (SPI) for distributed cross-pod subscription registry
 * and stream event fan-out (REQ-102 / Pattern C).
 */
public interface SubscriptionDistributionBus {

    /**
     * Broadcasts a stream frame to all pods in the cluster subscribed to this subscriptionId.
     */
    void publish(ClusterStreamFrame frame);

    /**
     * Registers a local consumer callback for cluster frames targeting the specified subscriptionId.
     */
    void subscribe(String subscriptionId, Consumer<ClusterStreamFrame> listener);

    /**
     * Unregisters the local callback for the subscriptionId.
     */
    void unsubscribe(String subscriptionId);

    /**
     * Returns current operational status and telemetry for this node and bus.
     */
    ClusterStatusDto getStatus();
}
