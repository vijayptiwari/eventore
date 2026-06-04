import type { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { EventoreClient } from './eventore-client.js';
import { allGuides } from './protocol-guide.js';

export function registerResources(server: McpServer, client: EventoreClient): void {
  server.resource(
    'eventore-config',
    'eventore://config',
    { description: 'Current Eventore deployment mode and capabilities', mimeType: 'application/json' },
    async () => {
      const config = await client.getConfig();
      return {
        contents: [
          {
            uri: 'eventore://config',
            mimeType: 'application/json',
            text: JSON.stringify(config, null, 2),
          },
        ],
      };
    },
  );

  server.resource(
    'eventore-connections',
    'eventore://connections',
    { description: 'All registered messaging connection profiles', mimeType: 'application/json' },
    async () => {
      const connections = await client.listConnections();
      return {
        contents: [
          {
            uri: 'eventore://connections',
            mimeType: 'application/json',
            text: JSON.stringify(connections, null, 2),
          },
        ],
      };
    },
  );

  server.resource(
    'eventore-protocol-guides',
    'eventore://protocol-guides',
    {
      description: 'Smart connection guides for Kafka, MQTT, JMS, Pulsar, RabbitMQ',
      mimeType: 'application/json',
    },
    async () => ({
      contents: [
        {
          uri: 'eventore://protocol-guides',
          mimeType: 'application/json',
          text: JSON.stringify(allGuides(), null, 2),
        },
      ],
    }),
  );
}
