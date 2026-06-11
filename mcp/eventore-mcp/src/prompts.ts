import { z } from 'zod';
import type { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import type { EventoreClient } from './eventore-client.js';
import { MCP_ARCHITECTURE } from './architecture-context.js';
import { ProtocolSchema } from './protocol-types.js';

function promptText(text: string) {
  return {
    messages: [
      {
        role: 'user' as const,
        content: { type: 'text' as const, text },
      },
    ],
  };
}

export function registerPrompts(server: McpServer, client: EventoreClient): void {
  server.prompt(
    'eventore_discover',
    'Onboarding: load deployment mode, control-plane registration, and which MCP tools apply.',
    async () => {
      const [config, plane] = await Promise.all([client.getConfig(), client.getControlPlane()]);
      const text = [
        'You are connected to Eventore via MCP. Follow this order:',
        '',
        '1. Deployment mode and allowed actions (from config):',
        JSON.stringify(
          {
            deploymentMode: config.deploymentMode,
            allowedActions: config.allowedActions,
            loadedModules: config.loadedModules,
          },
          null,
          2,
        ),
        '',
        '2. Control plane revision and active protocols (must be registered before data-plane calls):',
        JSON.stringify(
          {
            revision: plane.revision,
            activeProtocols: plane.activeProtocols,
            uiCascade: plane.uiCascade,
          },
          null,
          2,
        ),
        '',
        '3. Architecture map:',
        JSON.stringify(MCP_ARCHITECTURE, null, 2),
        '',
        'Use eventore_list_connections only for protocols listed as active. Use eventore_protocol_guide for broker URL hints.',
      ].join('\n');
      return promptText(text);
    },
  );

  server.prompt(
    'eventore_probe_broker',
    'Playbook: ephemeral connection probe (validate, list destinations, optional samples).',
    {
      protocol: ProtocolSchema.describe('Broker protocol'),
      brokerUrl: z.string().describe('Broker URL (see eventore_protocol_guide)'),
      destination: z.string().optional().describe('Topic/queue for sample consume'),
      consumeSamples: z.boolean().optional().default(false),
    },
    async (args) => {
      return promptText(
        [
          'Run a safe broker probe with Eventore MCP:',
          '',
          `Tool: eventore_quick_probe`,
          `Arguments: ${JSON.stringify(args, null, 2)}`,
          '',
          'The tool creates a temporary connection, validates, lists destinations, optionally consumes up to 10 messages, then deletes the profile.',
          'Check eventore_get_config first — READONLY mode blocks create/publish.',
          'Confirm protocol is registered: eventore_get_provider_status with the same protocol.',
        ].join('\n'),
      );
    },
  );

  server.prompt(
    'eventore_kafka_inspection',
    'Playbook: Kafka cluster metadata, consumer groups, lag, and topic search.',
    {
      connectionId: z.string().describe('Saved Kafka connection id'),
      groupId: z.string().optional().describe('Consumer group for lag'),
      topic: z.string().optional().describe('Topic for lag or search'),
    },
    async (args) => {
      return promptText(
        [
          'Kafka inspection sequence (data plane; connection must be KAFKA and registered):',
          '',
          `1. eventore_inspect_cluster — connectionId=${args.connectionId}`,
          '2. eventore_inspect_consumer_groups — same connectionId',
          args.groupId
            ? `3. eventore_inspect_lag — groupId=${args.groupId}${args.topic ? `, topic=${args.topic}` : ''}`
            : '3. (optional) eventore_inspect_lag — pick a groupId from step 2',
          args.topic
            ? `4. eventore_inspect_search — topic=${args.topic}`
            : '4. (optional) eventore_inspect_search — topic name required',
          '',
          'Admin topic/ACL changes require ADMIN mode: eventore_kafka_create_topic, eventore_kafka_delete_topic, eventore_kafka_list_acls.',
        ].join('\n'),
      );
    },
  );

  server.prompt(
    'eventore_rabbitmq_inspection',
    'Playbook: RabbitMQ queue inventory, depth, and message search.',
    {
      connectionId: z.string().describe('Saved RabbitMQ connection id'),
      queue: z.string().optional().describe('Queue name for detail, depth, or search'),
    },
    async (args) => {
      return promptText(
        [
          'RabbitMQ inspection sequence (connection must be RABBITMQ and registered):',
          '',
          `1. eventore_rabbitmq_list_queues — connectionId=${args.connectionId}`,
          args.queue
            ? `2. eventore_rabbitmq_queue_detail — queue=${args.queue}`
            : '2. (optional) eventore_rabbitmq_queue_detail — pick a queue from step 1',
          args.queue
            ? `3. eventore_rabbitmq_queue_depth — queue=${args.queue}`
            : '3. (optional) eventore_rabbitmq_queue_depth',
          args.queue
            ? `4. eventore_inspect_search — topic=${args.queue} (queue name)`
            : '4. (optional) eventore_inspect_search — queue name as topic',
          '',
          'Cross-check static matrix: resource eventore://capability-matrix',
        ].join('\n'),
      );
    },
  );

  server.prompt(
    'eventore_gcp_pubsub_inspection',
    'Playbook: GCP Pub/Sub topics, subscriptions, and backlog.',
    {
      connectionId: z.string().describe('Saved GCP_PUBSUB connection id'),
      subscription: z.string().optional().describe('Subscription id for backlog'),
    },
    async (args) => {
      return promptText(
        [
          'GCP Pub/Sub inspection sequence:',
          '',
          `1. eventore_gcp_list_topics — connectionId=${args.connectionId}`,
          '2. eventore_gcp_list_subscriptions — same connectionId',
          args.subscription
            ? `3. eventore_gcp_subscription_backlog — subscription=${args.subscription}`
            : '3. (optional) eventore_gcp_subscription_backlog — pick subscription from step 2',
          '',
          'Message search is not supported (501). Use pull consume via eventore_consume_messages for samples.',
        ].join('\n'),
      );
    },
  );

  server.prompt(
    'eventore_azure_servicebus_inspection',
    'Playbook: Azure Service Bus entities, subscriptions, peek, and backlog.',
    {
      connectionId: z.string().describe('Saved AZURE_SERVICE_BUS connection id'),
      entity: z.string().optional().describe('Queue or topic name'),
      subscription: z.string().optional().describe('Topic subscription name for peek/backlog'),
    },
    async (args) => {
      return promptText(
        [
          'Azure Service Bus inspection sequence:',
          '',
          `1. eventore_azure_list_entities — connectionId=${args.connectionId}`,
          '2. eventore_azure_list_subscriptions — topic subscriptions with DLQ counts',
          args.entity
            ? `3. eventore_azure_peek_messages — entity=${args.entity}${args.subscription ? `, subscription=${args.subscription}` : ''}`
            : '3. (optional) eventore_azure_peek_messages — non-destructive peek',
          args.entity
            ? `4. eventore_azure_entity_backlog — entityId=${args.entity}`
            : '4. (optional) eventore_azure_entity_backlog',
          '',
          'Peek uses partition=subscription name when entity is a topic.',
        ].join('\n'),
      );
    },
  );

  server.prompt(
    'eventore_mqtt_inspection',
    'Playbook: MQTT broker topics and topic filters.',
    {
      connectionId: z.string().describe('Saved MQTT connection id'),
      topic: z.string().optional().describe('Topic for detail'),
    },
    async (args) => {
      return promptText(
        [
          'MQTT inspection sequence:',
          '',
          `1. eventore_mqtt_list_topics — connectionId=${args.connectionId}`,
          args.topic
            ? `2. eventore_mqtt_topic_detail — topic=${args.topic}`
            : '2. (optional) eventore_mqtt_topic_detail',
          '',
          'Use eventore_consume_messages with destination=topic for live samples.',
        ].join('\n'),
      );
    },
  );

  server.prompt(
    'eventore_jms_inspection',
    'Playbook: JMS queues and topics on Artemis/ActiveMQ.',
    {
      connectionId: z.string().describe('Saved JMS connection id'),
      destination: z.string().optional().describe('Queue or topic name'),
    },
    async (args) => {
      return promptText(
        [
          'JMS inspection sequence:',
          '',
          `1. eventore_jms_list_destinations — connectionId=${args.connectionId}`,
          args.destination
            ? `2. eventore_jms_destination_detail — destination=${args.destination}`
            : '2. (optional) eventore_jms_destination_detail',
          '',
          'Publish/subscribe: set header destinationType=queue|topic.',
        ].join('\n'),
      );
    },
  );

  server.prompt(
    'eventore_pulsar_inspection',
    'Playbook: Pulsar topics, subscriptions, and backlog.',
    {
      connectionId: z.string().describe('Saved PULSAR connection id'),
      topic: z.string().optional().describe('Topic name'),
      subscription: z.string().optional().describe('Subscription for backlog'),
    },
    async (args) => {
      return promptText(
        [
          'Pulsar inspection sequence:',
          '',
          `1. eventore_pulsar_list_topics — connectionId=${args.connectionId}`,
          '2. eventore_inspect_consumer_groups — list subscriptions',
          args.topic && args.subscription
            ? `3. eventore_pulsar_subscription_backlog — topic=${args.topic}, subscription=${args.subscription}`
            : '3. (optional) eventore_pulsar_subscription_backlog',
        ].join('\n'),
      );
    },
  );

  server.prompt(
    'eventore_control_plane_ops',
    'Playbook: register or deregister stream providers (ADMIN + ADMIN_BROKER_OPS).',
    {
      protocol: ProtocolSchema.describe('Protocol to register or deregister'),
      action: z.enum(['register', 'deregister', 'status']),
    },
    async (args) => {
      const tool =
        args.action === 'register'
          ? 'eventore_register_provider'
          : args.action === 'deregister'
            ? 'eventore_deregister_provider'
            : 'eventore_get_provider_status';
      return promptText(
        [
          'Control plane operation (no direct broker I/O):',
          '',
          `Recommended tool: ${tool}`,
          `protocol: ${args.protocol}`,
          '',
          'Before register: eventore_get_control_plane — implementation must exist on classpath (Maven profile).',
          'After register: revision increments; UI cascade updates connectionProtocols / inspectProtocols.',
          'Deregister fails if this is the last active provider.',
          '',
          'Data-plane connection CRUD is separate — use eventore_create_connection after the provider is active.',
        ].join('\n'),
      );
    },
  );
}
