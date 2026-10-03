package com.eventore.connector.spi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SubscribeRequest {

    private String destination;
    /** When set, subscribes to multiple topics/destinations (Kafka, MQTT). */
    private List<String> destinations = new ArrayList<>();
    private String consumerGroup;
    /** Unique key for this subscription instance (prevents connector resource collisions). */
    private String subscriptionKey;
    private Map<String, String> options = new HashMap<>();
    private String replayMode;
    private String replayTimestamp;
    private Long replayOffset;

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public List<String> getDestinations() {
        return destinations == null ? List.of() : Collections.unmodifiableList(destinations);
    }

    public void setDestinations(List<String> destinations) {
        this.destinations = destinations != null ? new ArrayList<>(destinations) : new ArrayList<>();
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    public String getSubscriptionKey() {
        return subscriptionKey;
    }

    public void setSubscriptionKey(String subscriptionKey) {
        this.subscriptionKey = subscriptionKey;
    }

    public Map<String, String> getOptions() {
        return options == null ? Map.of() : Collections.unmodifiableMap(options);
    }

    public void setOptions(Map<String, String> options) {
        this.options = options != null ? new HashMap<>(options) : new HashMap<>();
    }

    public String getReplayMode() {
        if (replayMode != null) {
            return replayMode;
        }
        return options != null ? options.get("replayMode") : null;
    }

    public void setReplayMode(String replayMode) {
        this.replayMode = replayMode;
        if (replayMode != null) {
            if (this.options == null) {
                this.options = new HashMap<>();
            }
            this.options.put("replayMode", replayMode);
        }
    }

    public String getReplayTimestamp() {
        if (replayTimestamp != null) {
            return replayTimestamp;
        }
        return options != null ? options.get("replayTimestamp") : null;
    }

    public void setReplayTimestamp(String replayTimestamp) {
        this.replayTimestamp = replayTimestamp;
        if (replayTimestamp != null) {
            if (this.options == null) {
                this.options = new HashMap<>();
            }
            this.options.put("replayTimestamp", replayTimestamp);
        }
    }

    public Long getReplayOffset() {
        if (replayOffset != null) {
            return replayOffset;
        }
        if (options != null && options.containsKey("replayOffset")) {
            try {
                return Long.parseLong(options.get("replayOffset"));
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    public void setReplayOffset(Long replayOffset) {
        this.replayOffset = replayOffset;
        if (replayOffset != null) {
            if (this.options == null) {
                this.options = new HashMap<>();
            }
            this.options.put("replayOffset", String.valueOf(replayOffset));
        }
    }
}
