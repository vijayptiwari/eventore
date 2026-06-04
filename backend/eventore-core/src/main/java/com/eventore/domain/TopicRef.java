package com.eventore.domain;

public class TopicRef {

    private String name;
    private String type;
    private ProtocolType protocol;

    public TopicRef() {}

    public TopicRef(String name, String type, ProtocolType protocol) {
        this.name = name;
        this.type = type;
        this.protocol = protocol;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public ProtocolType getProtocol() {
        return protocol;
    }

    public void setProtocol(ProtocolType protocol) {
        this.protocol = protocol;
    }
}
