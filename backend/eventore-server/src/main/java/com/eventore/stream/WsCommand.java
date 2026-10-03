package com.eventore.stream;

import java.util.List;
import java.util.Map;

public class WsCommand {

    private String type;
    private String connectionId;
    private String destination;
    private List<String> topics;
    private String consumerGroup;
    private String subscriptionId;
    private String clientStreamId;
    private String headerRegex;
    private String bodyRegex;
    private Integer durationMinutes;
    private Map<String, String> options;
    private String replayMode;
    private String replayTimestamp;
    private Long replayOffset;
    private Boolean maskingEnabled;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    /** Rejects commands with a missing or blank {@code type} (after JSON deserialization). */
    public static void validateType(WsCommand command) {
        if (command == null || command.type == null || command.type.isBlank()) {
            throw new IllegalArgumentException("WebSocket command type is required");
        }
    }

    public String getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(String connectionId) {
        this.connectionId = connectionId;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public List<String> getTopics() {
        return topics;
    }

    public void setTopics(List<String> topics) {
        this.topics = topics;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(String subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public String getClientStreamId() {
        return clientStreamId;
    }

    public void setClientStreamId(String clientStreamId) {
        this.clientStreamId = clientStreamId;
    }

    public String getHeaderRegex() {
        return headerRegex;
    }

    public void setHeaderRegex(String headerRegex) {
        this.headerRegex = headerRegex;
    }

    public String getBodyRegex() {
        return bodyRegex;
    }

    public void setBodyRegex(String bodyRegex) {
        this.bodyRegex = bodyRegex;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public Map<String, String> getOptions() {
        return options;
    }

    public void setOptions(Map<String, String> options) {
        this.options = options;
    }

    public String getReplayMode() {
        return replayMode;
    }

    public void setReplayMode(String replayMode) {
        this.replayMode = replayMode;
    }

    public String getReplayTimestamp() {
        return replayTimestamp;
    }

    public void setReplayTimestamp(String replayTimestamp) {
        this.replayTimestamp = replayTimestamp;
    }

    public Long getReplayOffset() {
        return replayOffset;
    }

    public void setReplayOffset(Long replayOffset) {
        this.replayOffset = replayOffset;
    }

    public Boolean getMaskingEnabled() {
        return maskingEnabled;
    }

    public void setMaskingEnabled(Boolean maskingEnabled) {
        this.maskingEnabled = maskingEnabled;
    }
}
