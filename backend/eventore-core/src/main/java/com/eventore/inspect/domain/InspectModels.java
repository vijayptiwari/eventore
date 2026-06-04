package com.eventore.inspect.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class InspectModels {

    private InspectModels() {}

    public static class ClusterInfo {
        private String clusterId;
        private List<BrokerNode> brokers = new ArrayList<>();
        private Map<String, String> attributes = new HashMap<>();

        public String getClusterId() {
            return clusterId;
        }

        public void setClusterId(String clusterId) {
            this.clusterId = clusterId;
        }

        public List<BrokerNode> getBrokers() {
            return brokers;
        }

        public void setBrokers(List<BrokerNode> brokers) {
            this.brokers = brokers;
        }

        public Map<String, String> getAttributes() {
            return attributes;
        }

        public void setAttributes(Map<String, String> attributes) {
            this.attributes = attributes;
        }
    }

    public static class BrokerNode {
        private int id;
        private String host;
        private int port;
        private String rack;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getRack() {
            return rack;
        }

        public void setRack(String rack) {
            this.rack = rack;
        }
    }

    public static class ConsumerGroupSummary {
        private String groupId;
        private String state;
        private String protocolType;
        private int memberCount;

        public String getGroupId() {
            return groupId;
        }

        public void setGroupId(String groupId) {
            this.groupId = groupId;
        }

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }

        public String getProtocolType() {
            return protocolType;
        }

        public void setProtocolType(String protocolType) {
            this.protocolType = protocolType;
        }

        public int getMemberCount() {
            return memberCount;
        }

        public void setMemberCount(int memberCount) {
            this.memberCount = memberCount;
        }
    }

    public static class ConsumerGroupDetail {
        private String groupId;
        private String state;
        private String partitionAssignor;
        private List<GroupMember> members = new ArrayList<>();
        private List<GroupOffset> offsets = new ArrayList<>();

        public String getGroupId() {
            return groupId;
        }

        public void setGroupId(String groupId) {
            this.groupId = groupId;
        }

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }

        public String getPartitionAssignor() {
            return partitionAssignor;
        }

        public void setPartitionAssignor(String partitionAssignor) {
            this.partitionAssignor = partitionAssignor;
        }

        public List<GroupMember> getMembers() {
            return members;
        }

        public void setMembers(List<GroupMember> members) {
            this.members = members;
        }

        public List<GroupOffset> getOffsets() {
            return offsets;
        }

        public void setOffsets(List<GroupOffset> offsets) {
            this.offsets = offsets;
        }
    }

    public static class GroupMember {
        private String memberId;
        private String clientId;
        private String host;
        private List<String> assignments = new ArrayList<>();

        public String getMemberId() {
            return memberId;
        }

        public void setMemberId(String memberId) {
            this.memberId = memberId;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public List<String> getAssignments() {
            return assignments;
        }

        public void setAssignments(List<String> assignments) {
            this.assignments = assignments;
        }
    }

    public static class GroupOffset {
        private String topic;
        private int partition;
        private long offset;
        private long logEndOffset;
        private long lag;

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public int getPartition() {
            return partition;
        }

        public void setPartition(int partition) {
            this.partition = partition;
        }

        public long getOffset() {
            return offset;
        }

        public void setOffset(long offset) {
            this.offset = offset;
        }

        public long getLogEndOffset() {
            return logEndOffset;
        }

        public void setLogEndOffset(long logEndOffset) {
            this.logEndOffset = logEndOffset;
        }

        public long getLag() {
            return lag;
        }

        public void setLag(long lag) {
            this.lag = lag;
        }
    }

    public static class TopicDetail {
        private String name;
        private int partitionCount;
        private int replicationFactor;
        private List<TopicPartitionInfo> partitions = new ArrayList<>();
        private Map<String, String> config = new HashMap<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getPartitionCount() {
            return partitionCount;
        }

        public void setPartitionCount(int partitionCount) {
            this.partitionCount = partitionCount;
        }

        public int getReplicationFactor() {
            return replicationFactor;
        }

        public void setReplicationFactor(int replicationFactor) {
            this.replicationFactor = replicationFactor;
        }

        public List<TopicPartitionInfo> getPartitions() {
            return partitions;
        }

        public void setPartitions(List<TopicPartitionInfo> partitions) {
            this.partitions = partitions;
        }

        public Map<String, String> getConfig() {
            return config;
        }

        public void setConfig(Map<String, String> config) {
            this.config = config;
        }
    }

    public static class TopicPartitionInfo {
        private int partition;
        private int leader;
        private List<Integer> replicas = new ArrayList<>();
        private List<Integer> isr = new ArrayList<>();

        public int getPartition() {
            return partition;
        }

        public void setPartition(int partition) {
            this.partition = partition;
        }

        public int getLeader() {
            return leader;
        }

        public void setLeader(int leader) {
            this.leader = leader;
        }

        public List<Integer> getReplicas() {
            return replicas;
        }

        public void setReplicas(List<Integer> replicas) {
            this.replicas = replicas;
        }

        public List<Integer> getIsr() {
            return isr;
        }

        public void setIsr(List<Integer> isr) {
            this.isr = isr;
        }
    }

    public static class MessageSearchRequest {
        private String topic;
        private String partition;
        private String keyContains;
        private String payloadContains;
        private Long fromOffset;
        private Long toOffset;
        private Integer maxMessages = 50;
        private String startAt = "latest";

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public String getPartition() {
            return partition;
        }

        public void setPartition(String partition) {
            this.partition = partition;
        }

        public String getKeyContains() {
            return keyContains;
        }

        public void setKeyContains(String keyContains) {
            this.keyContains = keyContains;
        }

        public String getPayloadContains() {
            return payloadContains;
        }

        public void setPayloadContains(String payloadContains) {
            this.payloadContains = payloadContains;
        }

        public Long getFromOffset() {
            return fromOffset;
        }

        public void setFromOffset(Long fromOffset) {
            this.fromOffset = fromOffset;
        }

        public Long getToOffset() {
            return toOffset;
        }

        public void setToOffset(Long toOffset) {
            this.toOffset = toOffset;
        }

        public Integer getMaxMessages() {
            return maxMessages;
        }

        public void setMaxMessages(Integer maxMessages) {
            this.maxMessages = maxMessages;
        }

        public String getStartAt() {
            return startAt;
        }

        public void setStartAt(String startAt) {
            this.startAt = startAt;
        }
    }

    public static class ProtocolInspectCapabilities {
        private List<String> features = new ArrayList<>();

        public List<String> getFeatures() {
            return features;
        }

        public void setFeatures(List<String> features) {
            this.features = features;
        }
    }
}
