package com.eventore.platform;

import com.eventore.domain.CloudProvider;
import com.eventore.domain.ProtocolType;
import com.eventore.domain.StreamPlatform;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StreamPlatformCatalog {

    private static final List<StreamPlatformPreset> PRESETS = List.copyOf(build());

    private StreamPlatformCatalog() {}

    /** Returns the immutable preset catalog (built once). Callers must not mutate the presets. */
    public static List<StreamPlatformPreset> all() {
        return PRESETS;
    }

    private static List<StreamPlatformPreset> build() {
        List<StreamPlatformPreset> list = new ArrayList<>();
        addGenericOnPremPresets(list);
        addAwsPresets(list);
        addAzurePresets(list);
        addGcpPresets(list);
        addOcpPresets(list);
        addAdditionalOnPremPresets(list);
        return list;
    }

    private static void addGenericOnPremPresets(List<StreamPlatformPreset> list) {
        list.add(preset(
                StreamPlatform.GENERIC,
                "On-prem / generic",
                "Self-hosted broker using native protocol.",
                CloudProvider.ON_PREM,
                ProtocolType.KAFKA,
                "localhost:9092",
                Map.of(),
                List.of("username", "password"),
                List.of("full-inspect", "admin")));
    }

    private static void addAwsPresets(List<StreamPlatformPreset> list) {
        list.add(preset(
                StreamPlatform.AWS_MSK,
                "Amazon MSK",
                "Managed Kafka — use SASL/IAM against bootstrap brokers.",
                CloudProvider.AWS,
                ProtocolType.KAFKA,
                "boot-xxxxx.kafka.us-east-1.amazonaws.com:9098",
                Map.of(
                        "security.protocol", "SASL_SSL",
                        "sasl.mechanism", "AWS_MSK_IAM",
                        "cloud.kafka", "msk"),
                List.of("username", "password"),
                List.of("kafka-full", "cloud-aws")));
        list.add(preset(
                StreamPlatform.AWS_KINESIS,
                "Amazon Kinesis Data Streams",
                "Shard-based streaming (region in broker URL or property).",
                CloudProvider.AWS,
                ProtocolType.KINESIS,
                "us-east-1",
                Map.of("region", "us-east-1"),
                List.of("accessKeyId", "secretAccessKey"),
                List.of("streams", "publish", "subscribe", "inspect")));
        list.add(preset(
                StreamPlatform.AWS_MQ,
                "Amazon MQ",
                "Managed ActiveMQ/RabbitMQ — use JMS or RabbitMQ protocol.",
                CloudProvider.AWS,
                ProtocolType.JMS,
                "ssl://b-xxxxx.mq.us-east-1.amazonaws.com:61617",
                Map.of("queue", "eventore.queue"),
                List.of("username", "password"),
                List.of("jms", "cloud-aws")));
        list.add(preset(
                StreamPlatform.AWS_IOT_CORE,
                "AWS IoT Core (MQTT)",
                "MQTT over TLS to IoT endpoint.",
                CloudProvider.AWS,
                ProtocolType.MQTT,
                "ssl://xxxx.iot.us-east-1.amazonaws.com:8883",
                Map.of("topicFilter", "#"),
                List.of("username", "password"),
                List.of("mqtt", "cloud-aws")));
        // AWS_SQS omitted until a dedicated connector exists (StreamPlatform.AWS_SQS reserved).
    }

    private static void addAzurePresets(List<StreamPlatformPreset> list) {
        list.add(preset(
                StreamPlatform.AZURE_EVENT_HUBS,
                "Azure Event Hubs (Kafka surface)",
                "Kafka protocol to Event Hubs namespace.",
                CloudProvider.AZURE,
                ProtocolType.KAFKA,
                "namespace.servicebus.windows.net:9093",
                Map.of(
                        "security.protocol", "SASL_SSL",
                        "sasl.mechanism", "PLAIN",
                        "cloud.kafka", "eventhubs"),
                List.of("username", "password"),
                List.of("kafka-full", "cloud-azure")));
        list.add(preset(
                StreamPlatform.AZURE_SERVICE_BUS,
                "Azure Service Bus",
                "Queues and topics via connection string.",
                CloudProvider.AZURE,
                ProtocolType.AZURE_SERVICE_BUS,
                "my-namespace.servicebus.windows.net",
                Map.of("entityType", "queue", "entityPath", "eventore"),
                List.of("connectionString"),
                List.of("queues", "topics", "publish", "subscribe")));
        list.add(preset(
                StreamPlatform.AZURE_IOT_HUB,
                "Azure IoT Hub (MQTT)",
                "MQTT to IoT Hub endpoint.",
                CloudProvider.AZURE,
                ProtocolType.MQTT,
                "ssl://namespace.azure-devices.net:8883",
                Map.of("topicFilter", "devices/+/messages/#"),
                List.of("username", "password"),
                List.of("mqtt", "cloud-azure")));
    }

    private static void addGcpPresets(List<StreamPlatformPreset> list) {
        list.add(preset(
                StreamPlatform.GCP_PUBSUB,
                "Google Cloud Pub/Sub",
                "Project ID as broker URL; JSON key in credentials.",
                CloudProvider.GCP,
                ProtocolType.GCP_PUBSUB,
                "my-gcp-project",
                Map.of("subscription", "eventore-sub"),
                List.of("serviceAccountJson"),
                List.of("topics", "subscriptions", "publish", "subscribe")));
        list.add(preset(
                StreamPlatform.GCP_MANAGED_KAFKA,
                "GCP Managed Service for Apache Kafka",
                "Kafka bootstrap from GCP console.",
                CloudProvider.GCP,
                ProtocolType.KAFKA,
                "bootstrap.kafka.us-central1.gcp.com:9092",
                Map.of("cloud.kafka", "gcp-managed"),
                List.of("username", "password"),
                List.of("kafka-full", "cloud-gcp")));
    }

    private static void addOcpPresets(List<StreamPlatformPreset> list) {
        list.add(preset(
                StreamPlatform.OCP_AMQ_STREAMS,
                "OpenShift AMQ Streams",
                "Strimzi/AMQ Kafka route on OCP.",
                CloudProvider.OCP,
                ProtocolType.KAFKA,
                "kafka-bootstrap-myproject.apps.cluster.example.com:443",
                Map.of("security.protocol", "SASL_SSL", "cloud.kafka", "ocp-amq"),
                List.of("username", "password"),
                List.of("kafka-full", "cloud-ocp")));
        list.add(preset(
                StreamPlatform.OCP_STRIMZI_KAFKA,
                "OpenShift Strimzi Kafka",
                "Strimzi cluster bootstrap and TLS certs via properties.",
                CloudProvider.OCP,
                ProtocolType.KAFKA,
                "my-cluster-kafka-bootstrap:9092",
                Map.of("security.protocol", "SSL", "cloud.kafka", "strimzi"),
                List.of("username", "password"),
                List.of("kafka-full", "cloud-ocp")));
    }

    private static void addAdditionalOnPremPresets(List<StreamPlatformPreset> list) {
        list.add(preset(
                StreamPlatform.GENERIC,
                "Apache Pulsar",
                "Native Pulsar binary protocol.",
                CloudProvider.ON_PREM,
                ProtocolType.PULSAR,
                "pulsar://localhost:6650",
                Map.of("adminUrl", "http://localhost:8080"),
                List.of(),
                List.of("topics", "subscriptions", "backlog", "admin")));
        list.add(preset(
                StreamPlatform.GENERIC,
                "RabbitMQ",
                "AMQP + management plugin.",
                CloudProvider.ON_PREM,
                ProtocolType.RABBITMQ,
                "localhost:5672",
                Map.of("managementPort", "15672", "queue", "eventore.queue"),
                List.of("username", "password"),
                List.of("queues", "purge", "message-get")));
    }

    private static StreamPlatformPreset preset(
            StreamPlatform platform,
            String label,
            String description,
            CloudProvider cloud,
            ProtocolType protocol,
            String brokerHint,
            Map<String, String> props,
            List<String> creds,
            List<String> features) {
        StreamPlatformPreset p = new StreamPlatformPreset();
        p.setPlatform(platform);
        p.setLabel(label);
        p.setDescription(description);
        p.setCloudProvider(cloud);
        p.setProtocol(protocol);
        p.setBrokerUrlHint(brokerHint);
        p.setDefaultProperties(new LinkedHashMap<>(props));
        p.setCredentialFields(creds);
        p.setFeatures(features);
        return p;
    }
}
