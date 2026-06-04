package com.eventore.stream;

import com.eventore.domain.UnifiedMessage;

public class StreamFrame {

    private String type;
    private String subscriptionId;
    private String clientStreamId;
    private UnifiedMessage message;
    private String detail;
    /** Epoch millis when a timed live view expires. */
    private Long expiresAt;

    public StreamFrame() {}

    public StreamFrame(String type, String subscriptionId, UnifiedMessage message, String detail) {
        this(type, subscriptionId, null, message, detail);
    }

    public StreamFrame(
            String type, String subscriptionId, String clientStreamId, UnifiedMessage message, String detail) {
        this.type = type;
        this.subscriptionId = subscriptionId;
        this.clientStreamId = clientStreamId;
        this.message = message;
        this.detail = detail;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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

    public UnifiedMessage getMessage() {
        return message;
    }

    public void setMessage(UnifiedMessage message) {
        this.message = message;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public Long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Long expiresAt) {
        this.expiresAt = expiresAt;
    }
}
