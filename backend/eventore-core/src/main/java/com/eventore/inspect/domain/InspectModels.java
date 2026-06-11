package com.eventore.inspect.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Inspect DTOs. Collection getters return unmodifiable views; use the matching
 * {@code put*}/{@code add*} helpers or setters to populate instances.
 */
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
            return Collections.unmodifiableList(brokers);
        }

        public void setBrokers(List<BrokerNode> brokers) {
            this.brokers = brokers != null ? new ArrayList<>(brokers) : new ArrayList<>();
        }

        public void addBroker(BrokerNode broker) {
            if (broker != null) {
                brokers.add(broker);
            }
        }

        public Map<String, String> getAttributes() {
            return Collections.unmodifiableMap(attributes);
        }

        public void setAttributes(Map<String, String> attributes) {
            this.attributes = attributes != null ? new HashMap<>(attributes) : new HashMap<>();
        }

        public void putAttribute(String key, String value) {
            if (key != null && value != null) {
                attributes.put(key, value);
            }
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
            return Collections.unmodifiableList(members);
        }

        public void setMembers(List<GroupMember> members) {
            this.members = members != null ? new ArrayList<>(members) : new ArrayList<>();
        }

        public void addMember(GroupMember member) {
            if (member != null) {
                members.add(member);
            }
        }

        public List<GroupOffset> getOffsets() {
            return Collections.unmodifiableList(offsets);
        }

        public void setOffsets(List<GroupOffset> offsets) {
            this.offsets = offsets != null ? new ArrayList<>(offsets) : new ArrayList<>();
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
            return Collections.unmodifiableList(assignments);
        }

        public void setAssignments(List<String> assignments) {
            this.assignments = assignments != null ? new ArrayList<>(assignments) : new ArrayList<>();
        }

        public void addAssignment(String assignment) {
            if (assignment != null) {
                assignments.add(assignment);
            }
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
            return Collections.unmodifiableList(partitions);
        }

        public void setPartitions(List<TopicPartitionInfo> partitions) {
            this.partitions = partitions != null ? new ArrayList<>(partitions) : new ArrayList<>();
        }

        public void addPartition(TopicPartitionInfo partition) {
            if (partition != null) {
                partitions.add(partition);
            }
        }

        public Map<String, String> getConfig() {
            return Collections.unmodifiableMap(config);
        }

        public void setConfig(Map<String, String> config) {
            this.config = config != null ? new HashMap<>(config) : new HashMap<>();
        }

        public void putConfig(String key, String value) {
            if (key != null && value != null) {
                config.put(key, value);
            }
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
            return Collections.unmodifiableList(replicas);
        }

        public void setReplicas(List<Integer> replicas) {
            this.replicas = replicas != null ? new ArrayList<>(replicas) : new ArrayList<>();
        }

        public void addReplica(int replica) {
            replicas.add(replica);
        }

        public List<Integer> getIsr() {
            return Collections.unmodifiableList(isr);
        }

        public void setIsr(List<Integer> isr) {
            this.isr = isr != null ? new ArrayList<>(isr) : new ArrayList<>();
        }

        public void addIsr(int node) {
            isr.add(node);
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
            return Collections.unmodifiableList(features);
        }

        public void setFeatures(List<String> features) {
            this.features = features != null ? new ArrayList<>(features) : new ArrayList<>();
        }
    }
}
