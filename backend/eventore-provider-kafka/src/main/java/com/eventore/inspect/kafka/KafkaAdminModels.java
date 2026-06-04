package com.eventore.inspect.kafka;

import java.util.LinkedHashMap;
import java.util.Map;

public final class KafkaAdminModels {

    private KafkaAdminModels() {}

    public static class KafkaAclEntry {
        private String resourceType;
        private String resourceName;
        private String patternType = "LITERAL";
        private String principal;
        private String host = "*";
        private String operation;
        private String permissionType = "ALLOW";

        public String getResourceType() {
            return resourceType;
        }

        public void setResourceType(String resourceType) {
            this.resourceType = resourceType;
        }

        public String getResourceName() {
            return resourceName;
        }

        public void setResourceName(String resourceName) {
            this.resourceName = resourceName;
        }

        public String getPatternType() {
            return patternType;
        }

        public void setPatternType(String patternType) {
            this.patternType = patternType;
        }

        public String getPrincipal() {
            return principal;
        }

        public void setPrincipal(String principal) {
            this.principal = principal;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public String getOperation() {
            return operation;
        }

        public void setOperation(String operation) {
            this.operation = operation;
        }

        public String getPermissionType() {
            return permissionType;
        }

        public void setPermissionType(String permissionType) {
            this.permissionType = permissionType;
        }
    }

    public static class CreateTopicRequest {
        private String name;
        private int partitions = 1;
        private short replicationFactor = 1;
        private Map<String, String> configs = new LinkedHashMap<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getPartitions() {
            return partitions;
        }

        public void setPartitions(int partitions) {
            this.partitions = partitions;
        }

        public short getReplicationFactor() {
            return replicationFactor;
        }

        public void setReplicationFactor(short replicationFactor) {
            this.replicationFactor = replicationFactor;
        }

        public Map<String, String> getConfigs() {
            return configs;
        }

        public void setConfigs(Map<String, String> configs) {
            this.configs = configs != null ? configs : new LinkedHashMap<>();
        }
    }

    public static class AlterTopicConfigsRequest {
        private Map<String, String> configs = new LinkedHashMap<>();

        public Map<String, String> getConfigs() {
            return configs;
        }

        public void setConfigs(Map<String, String> configs) {
            this.configs = configs != null ? configs : new LinkedHashMap<>();
        }
    }

    public static class FlushTopicRequest {
        /** ALL = delete all records; PARTITION = single partition via partition field */
        private String mode = "ALL";
        private Integer partition;

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public Integer getPartition() {
            return partition;
        }

        public void setPartition(Integer partition) {
            this.partition = partition;
        }
    }

    public static class ReplaceAclRequest {
        private KafkaAclEntry oldBinding;
        private KafkaAclEntry newBinding;

        public KafkaAclEntry getOldBinding() {
            return oldBinding;
        }

        public void setOldBinding(KafkaAclEntry oldBinding) {
            this.oldBinding = oldBinding;
        }

        public KafkaAclEntry getNewBinding() {
            return newBinding;
        }

        public void setNewBinding(KafkaAclEntry newBinding) {
            this.newBinding = newBinding;
        }
    }

    public static class PublishResult {
        private String topic;
        private Integer partition;
        private Long offset;
        private String status = "published";

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public Integer getPartition() {
            return partition;
        }

        public void setPartition(Integer partition) {
            this.partition = partition;
        }

        public Long getOffset() {
            return offset;
        }

        public void setOffset(Long offset) {
            this.offset = offset;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}
