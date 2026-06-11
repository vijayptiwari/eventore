import type { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { MCP_ARCHITECTURE } from './architecture-context.js';
import { INSPECTOR_CAPABILITY_MATRIX } from './capability-matrix.js';
import { EventoreClient } from './eventore-client.js';
import { allGuides } from './protocol-guide.js';

export function registerResources(server: McpServer, client: EventoreClient): void {
  server.resource(
    'eventore-config',
    'eventore://config',
    {
      description:
        'Deployment mode, allowed actions, loaded modules, supported protocols, and embedded controlPlane UI cascade',
      mimeType: 'application/json',
    },
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
    'eventore-control-plane',
    'eventore://control-plane',
    {
      description:
        'Live control-plane snapshot: revision, active protocols, provider descriptors, UI cascade',
      mimeType: 'application/json',
    },
    async () => {
      const plane = await client.getControlPlane();
      return {
        contents: [
          {
            uri: 'eventore://control-plane',
            mimeType: 'application/json',
            text: JSON.stringify(plane, null, 2),
          },
        ],
      };
    },
  );

  server.resource(
    'eventore-providers',
    'eventore://providers',
    {
      description: 'Registered and available stream provider descriptors from the control plane',
      mimeType: 'application/json',
    },
    async () => {
      const providers = await client.listProviders();
      return {
        contents: [
          {
            uri: 'eventore://providers',
            mimeType: 'application/json',
            text: JSON.stringify(providers, null, 2),
          },
        ],
      };
    },
  );

  server.resource(
    'eventore-connections',
    'eventore://connections',
    {
      description: 'Data plane: all messaging connection profiles (broker endpoints, not provider registration)',
      mimeType: 'application/json',
    },
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
      description:
        'Connection and publish/subscribe hints per protocol (static; cross-check eventore://control-plane for registered protocols)',
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

  server.resource(
    'eventore-capability-matrix',
    'eventore://capability-matrix',
    {
      description: 'Inspector parity matrix per protocol (static; cross-check live capabilities per connection)',
      mimeType: 'application/json',
    },
    async () => ({
      contents: [
        {
          uri: 'eventore://capability-matrix',
          mimeType: 'application/json',
          text: JSON.stringify(INSPECTOR_CAPABILITY_MATRIX, null, 2),
        },
      ],
    }),
  );

  server.resource(
    'eventore-architecture',
    'eventore://architecture',
    {
      description: 'How MCP maps to Eventore control plane vs data plane APIs and recommended agent workflow',
      mimeType: 'application/json',
    },
    async () => ({
      contents: [
        {
          uri: 'eventore://architecture',
          mimeType: 'application/json',
          text: JSON.stringify(MCP_ARCHITECTURE, null, 2),
        },
      ],
    }),
  );
}
