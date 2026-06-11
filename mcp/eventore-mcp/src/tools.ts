import { z } from 'zod';
import type { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { EventoreClient, type ConnectionProfile } from './eventore-client.js';
import { allGuides, getProtocolGuide, suggestProtocol } from './protocol-guide.js';
import { ProtocolSchema, type ProtocolType } from './protocol-types.js';

function inspectToolResult(data: unknown) {
  return { content: [{ type: 'text' as const, text: JSON.stringify(data, null, 2) }] };
}

async function inspectToolResultFromError(error: unknown) {
  const msg = error instanceof Error ? error.message : String(error);
  if (msg.includes('501') || msg.includes('EVT-1501') || msg.toLowerCase().includes('not implemented')) {
    return {
      content: [{ type: 'text' as const, text: `501 Not implemented: ${msg}` }],
    };
  }
  throw error;
}

export function registerTools(server: McpServer, client: EventoreClient): void {
  server.tool(
    'eventore_get_config',
    'Core config: deployment mode, allowed actions, supported protocols, loadedModules, and embedded controlPlane UI cascade. Use controlPlane.uiCascade.inspectProtocols to choose eventore_inspect_* tools; adminProtocols gates eventore_kinesis_list_shards.',
    {},
    async () => {
      const config = await client.getConfig();
      return {
        content: [{ type: 'text', text: JSON.stringify(config, null, 2) }],
      };
    },
  );

  server.tool(
    'eventore_list_connections',
    'Data plane: list saved broker connection profiles. Protocol must be active in the control plane.',
    {},
    async () => {
      const list = await client.listConnections();
      return {
        content: [{ type: 'text', text: JSON.stringify(list, null, 2) }],
      };
    },
  );

  server.tool(
    'eventore_create_connection',
    'Data plane: create a broker connection profile. Provider must be registered (see eventore_get_control_plane). READONLY rejects.',
    {
      name: z.string().describe('Human-readable connection name'),
      protocol: ProtocolSchema,
      brokerUrl: z.string().describe('Broker address (see eventore_protocol_guide for examples)'),
      properties: z.record(z.string()).optional(),
      credentials: z.record(z.string()).optional().describe('username/password etc.'),
    },
    async (args) => {
      const profile: ConnectionProfile = {
        name: args.name,
        protocol: args.protocol as ProtocolType,
        brokerUrl: args.brokerUrl,
        properties: args.properties,
        credentials: args.credentials,
      };
      const created = await client.createConnection(profile);
      return {
        content: [{ type: 'text', text: JSON.stringify(created, null, 2) }],
      };
    },
  );

  server.tool(
    'eventore_update_connection',
    'Data plane: update an existing connection profile (name, brokerUrl, properties, credentials).',
    {
      connectionId: z.string(),
      name: z.string(),
      protocol: ProtocolSchema,
      brokerUrl: z.string(),
      properties: z.record(z.string()).optional(),
      credentials: z.record(z.string()).optional(),
    },
    async (args) => {
      const updated = await client.updateConnection(args.connectionId, {
        name: args.name,
        protocol: args.protocol as ProtocolType,
        brokerUrl: args.brokerUrl,
        properties: args.properties,
        credentials: args.credentials,
      });
      return {
        content: [{ type: 'text', text: JSON.stringify(updated, null, 2) }],
      };
    },
  );

  server.tool(
    'eventore_delete_connection',
    'Data plane: delete a connection and stop its active subscriptions.',
    { connectionId: z.string() },
    async (args) => {
      await client.deleteConnection(args.connectionId);
      return {
        content: [{ type: 'text', text: JSON.stringify({ deleted: args.connectionId }) }],
      };
    },
  );

  server.tool(
    'eventore_validate_connection',
    'Data plane: test broker connectivity for a saved connection without starting a long-lived consumer.',
    { connectionId: z.string() },
    async (args) => {
      const result = await client.validateConnection(args.connectionId);
      return {
        content: [{ type: 'text', text: JSON.stringify(result, null, 2) }],
      };
    },
  );

  server.tool(
    'eventore_list_destinations',
    'Data plane: list topics, queues, or destinations on a connection.',
    { connectionId: z.string() },
    async (args) => {
      const dests = await client.listDestinations(args.connectionId);
      return {
        content: [{ type: 'text', text: JSON.stringify(dests, null, 2) }],
      };
    },
  );

  server.tool(
    'eventore_publish_message',
    'Data plane: publish a message to a destination. Blocked in READONLY mode.',
    {
      connectionId: z.string(),
      destination: z.string(),
      payload: z.string(),
      headers: z.record(z.string()).optional(),
    },
    async (args) => {
      const result = await client.publish(args.connectionId, {
        destination: args.destination,
        payload: args.payload,
        headers: args.headers,
      });
      return {
        content: [{ type: 'text', text: JSON.stringify(result, null, 2) }],
      };
    },
  );

  server.tool(
    'eventore_consume_messages',
    'Data plane: subscribe via backend SSE (/api/v1/stream), collect samples, then unsubscribe. UI uses WebSocket; MCP uses SSE.',
    {
      connectionId: z.string(),
      destination: z.string(),
      consumerGroup: z.string().optional(),
      maxMessages: z.number().int().min(1).max(100).optional().default(20),
      timeoutMs: z.number().int().min(1000).max(60000).optional().default(10000),
    },
    async (args) => {
      const sub = await client.subscribe(args.connectionId, {
        destination: args.destination,
        consumerGroup: args.consumerGroup,
      });
      try {
        const messages = await client.consumeMessages(sub.subscriptionId, {
          maxMessages: args.maxMessages,
          timeoutMs: args.timeoutMs,
          sseUrl: sub.sseUrl,
        });
        return {
          content: [
            {
              type: 'text',
              text: JSON.stringify(
                { subscriptionId: sub.subscriptionId, messageCount: messages.length, messages },
                null,
                2,
              ),
            },
          ],
        };
      } finally {
        await client.unsubscribe(args.connectionId, sub.subscriptionId);
      }
    },
  );

  server.tool(
    'eventore_protocol_guide',
    'Smart helper: connection/publish/subscribe field hints per protocol, or suggest protocols from a natural-language hint.',
    {
      protocol: ProtocolSchema.optional(),
      hint: z.string().optional().describe('e.g. "mosquitto iot broker" or "kafka cluster"'),
    },
    async (args) => {
      if (args.hint && !args.protocol) {
        const suggested = suggestProtocol(args.hint);
        const guides = suggested.map((p) => getProtocolGuide(p));
        return {
          content: [
            {
              type: 'text',
              text: JSON.stringify({ hint: args.hint, suggestedProtocols: suggested, guides }, null, 2),
            },
          ],
        };
      }
      if (args.protocol) {
        return {
          content: [
            { type: 'text', text: JSON.stringify(getProtocolGuide(args.protocol), null, 2) },
          ],
        };
      }
      return {
        content: [{ type: 'text', text: JSON.stringify(allGuides(), null, 2) }],
      };
    },
  );

  server.tool(
    'eventore_quick_probe',
    'Smart workflow: create a temporary connection, validate, optionally list destinations or consume sample messages, then delete. Ideal for agent discovery.',
    {
      name: z.string().default('mcp-probe'),
      protocol: ProtocolSchema,
      brokerUrl: z.string(),
      destination: z.string().optional(),
      consumeSamples: z.boolean().optional().default(false),
      properties: z.record(z.string()).optional(),
      credentials: z.record(z.string()).optional(),
    },
    async (args) => {
      const profile = await client.createConnection({
        name: `${args.name}-${Date.now()}`,
        protocol: args.protocol as ProtocolType,
        brokerUrl: args.brokerUrl,
        properties: args.properties,
        credentials: args.credentials,
      });
      const connectionId = profile.id!;
      const report: Record<string, unknown> = { connectionId, protocol: args.protocol };

      try {
        report.validate = await client.validateConnection(connectionId);
        report.destinations = await client.listDestinations(connectionId);

        if (args.consumeSamples && args.destination) {
          const sub = await client.subscribe(connectionId, { destination: args.destination });
          try {
            report.samples = await client.consumeMessages(sub.subscriptionId, {
              maxMessages: 10,
              timeoutMs: 8000,
              sseUrl: sub.sseUrl,
            });
          } finally {
            await client.unsubscribe(connectionId, sub.subscriptionId);
          }
        }
      } finally {
        await client.deleteConnection(connectionId);
        report.cleanedUp = true;
      }

      return {
        content: [{ type: 'text', text: JSON.stringify(report, null, 2) }],
      };
    },
  );

  server.tool(
    'eventore_inspect_capabilities',
    'Inspect API feature tokens for a connection (use before other inspect tools).',
    { connectionId: z.string() },
    async (args) => {
      try {
        const data = await client.inspectCapabilities(args.connectionId);
        return inspectToolResult(data);
      } catch (error) {
        return inspectToolResultFromError(error);
      }
    },
  );

  server.tool(
    'eventore_inspect_topics',
    'List topics or queues on a connection with optional name filter.',
    {
      connectionId: z.string(),
      filter: z.string().optional(),
    },
    async (args) => {
      try {
        const data = await client.inspectTopics(args.connectionId, args.filter);
        return inspectToolResult(data);
      } catch (error) {
        return inspectToolResultFromError(error);
      }
    },
  );

  server.tool(
    'eventore_inspect_topic',
    'Describe one topic or queue on a connection.',
    {
      connectionId: z.string(),
      topic: z.string(),
    },
    async (args) => {
      try {
        const data = await client.inspectTopic(args.connectionId, args.topic);
        return inspectToolResult(data);
      } catch (error) {
        return inspectToolResultFromError(error);
      }
    },
  );

  server.tool(
    'eventore_kinesis_list_shards',
    'List Kinesis shards for a stream (requires KINESIS in adminProtocols).',
    {
      connectionId: z.string(),
      streamName: z.string(),
    },
    async (args) => {
      try {
        const data = await client.kinesisListShards(args.connectionId, args.streamName);
        return inspectToolResult(data);
      } catch (error) {
        return inspectToolResultFromError(error);
      }
    },
  );

  server.tool(
    'eventore_inspect_cluster',
    'Kafka/Pulsar cluster and broker metadata for a connection.',
    { connectionId: z.string() },
    async (args) => {
      const data = await client.inspectCluster(args.connectionId);
      return { content: [{ type: 'text', text: JSON.stringify(data, null, 2) }] };
    },
  );

  server.tool(
    'eventore_inspect_consumer_groups',
    'List consumer groups (Kafka) or subscriptions (Pulsar).',
    { connectionId: z.string() },
    async (args) => {
      const data = await client.inspectConsumerGroups(args.connectionId);
      return { content: [{ type: 'text', text: JSON.stringify(data, null, 2) }] };
    },
  );

  server.tool(
    'eventore_inspect_lag',
    'Consumer group lag per topic/partition (Kafka).',
    {
      connectionId: z.string(),
      groupId: z.string(),
      topic: z.string().optional(),
    },
    async (args) => {
      const data = await client.inspectLag(args.connectionId, args.groupId, args.topic);
      return { content: [{ type: 'text', text: JSON.stringify(data, null, 2) }] };
    },
  );

  server.tool(
    'eventore_kafka_publish',
    'Publish a Kafka message with optional key, partition, and record headers (ADMIN/DEV).',
    {
      connectionId: z.string(),
      topic: z.string(),
      payload: z.string(),
      key: z.string().optional(),
      partition: z.number().optional(),
      headers: z.record(z.string()).optional(),
      flush: z.boolean().optional(),
    },
    async (args) => {
      const headers: Record<string, string> = { ...(args.headers ?? {}) };
      if (args.key) headers.key = args.key;
      if (args.partition != null) headers.partition = String(args.partition);
      const data = await client.kafkaPublish(
        args.connectionId,
        { destination: args.topic, payload: args.payload, headers },
        args.flush ?? false,
      );
      return { content: [{ type: 'text', text: JSON.stringify(data, null, 2) }] };
    },
  );

  server.tool(
    'eventore_kafka_create_topic',
    'Create a Kafka topic (ADMIN mode only).',
    {
      connectionId: z.string(),
      name: z.string(),
      partitions: z.number().optional(),
      replicationFactor: z.number().optional(),
    },
    async (args) => {
      const data = await client.kafkaCreateTopic(args.connectionId, {
        name: args.name,
        partitions: args.partitions ?? 1,
        replicationFactor: args.replicationFactor ?? 1,
      });
      return { content: [{ type: 'text', text: JSON.stringify(data, null, 2) }] };
    },
  );

  server.tool(
    'eventore_kafka_delete_topic',
    'Delete a Kafka topic (ADMIN mode only).',
    { connectionId: z.string(), topic: z.string() },
    async (args) => {
      const data = await client.kafkaDeleteTopic(args.connectionId, args.topic);
      return { content: [{ type: 'text', text: JSON.stringify(data, null, 2) }] };
    },
  );

  server.tool(
    'eventore_kafka_list_acls',
    'List Kafka ACL bindings (ADMIN mode only).',
    {
      connectionId: z.string(),
      resourceType: z.string().optional(),
      resourceName: z.string().optional(),
    },
    async (args) => {
      const data = await client.kafkaListAcls(args.connectionId, args.resourceType, args.resourceName);
      return { content: [{ type: 'text', text: JSON.stringify(data, null, 2) }] };
    },
  );

  server.tool(
    'eventore_inspect_search',
    'Search/sample messages on a Kafka topic with optional payload/key filters.',
    {
      connectionId: z.string(),
      topic: z.string(),
      payloadContains: z.string().optional(),
      keyContains: z.string().optional(),
      maxMessages: z.number().optional(),
    },
    async (args) => {
      try {
        const data = await client.inspectSearch(args.connectionId, {
          topic: args.topic,
          payloadContains: args.payloadContains,
          keyContains: args.keyContains,
          maxMessages: args.maxMessages ?? 30,
          startAt: 'latest',
        });
        return inspectToolResult(data);
      } catch (error) {
        return inspectToolResultFromError(error);
      }
    },
  );
}
