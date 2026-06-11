import type { Page } from '@playwright/test';

export const appConfig = {
  deploymentMode: 'ADMIN',
  allowedActions: [
    'MANAGE_CONNECTIONS',
    'BROWSE_DESTINATIONS',
    'PUBLISH',
    'SUBSCRIBE',
  ],
  supportedProtocols: ['KAFKA', 'RABBITMQ'],
  loadedModules: ['kafka', 'rabbitmq'],
};

export const controlPlane = {
  revision: 1,
  openApiStreams: ['kafka'],
  uiCascade: {
    connectionProtocols: ['KAFKA', 'RABBITMQ'],
    inspectProtocols: ['KAFKA'],
    adminProtocols: ['KAFKA'],
    platformFilterProtocols: ['KAFKA', 'RABBITMQ'],
  },
};

export const connections = [
  {
    id: 'conn-kafka-1',
    name: 'Local Kafka',
    protocol: 'KAFKA',
    cloudProvider: 'ON_PREM',
    streamPlatform: 'GENERIC',
    brokerUrl: 'localhost:9092',
    properties: {},
    hasCredentials: false,
  },
];

export const platforms = [
  {
    key: 'generic-kafka',
    label: 'Apache Kafka',
    protocol: 'KAFKA',
    cloudProvider: 'ON_PREM',
    streamPlatform: 'GENERIC',
    defaultBrokerUrl: 'localhost:9092',
    properties: {},
  },
];

export const destinations = [
  { name: 'orders', type: 'topic', protocol: 'KAFKA' },
  { name: 'payments', type: 'topic', protocol: 'KAFKA' },
];

export const diagnosticsSubscriptions = [
  {
    subscriptionId: 'sub-1',
    connectionId: 'conn-kafka-1',
    connectionName: 'Local Kafka',
    protocol: 'KAFKA',
    destination: 'orders',
    transport: 'SSE',
    messageCount: 3,
    lastError: null,
    startedAt: '2026-06-11T10:00:00Z',
    status: 'ACTIVE',
  },
];

export const validationHistory = [
  { timestamp: '2026-06-11T09:55:00Z', status: 'OK', message: 'ok' },
];

/** Stubs every backend API route the SPA calls so E2E runs without a server. */
export async function mockApi(page: Page): Promise<void> {
  await page.route('**/api/v1/config', (route) =>
    route.fulfill({ json: appConfig }),
  );
  await page.route('**/api/v1/control/plane', (route) =>
    route.fulfill({ json: controlPlane }),
  );
  await page.route('**/api/v1/platforms', (route) =>
    route.fulfill({ json: platforms }),
  );
  await page.route('**/api/v1/connections', (route) => {
    if (route.request().method() === 'POST') {
      const body = route.request().postDataJSON() as Record<string, unknown>;
      return route.fulfill({ json: { ...body, id: 'conn-new-1' } });
    }
    return route.fulfill({ json: connections });
  });
  await page.route('**/api/v1/connections/*/destinations', (route) =>
    route.fulfill({ json: destinations }),
  );
  await page.route('**/api/v1/connections/*/publish', (route) =>
    route.fulfill({ status: 204, body: '' }),
  );
  await page.route('**/api/v1/diagnostics/subscriptions', (route) =>
    route.fulfill({ json: diagnosticsSubscriptions }),
  );
  await page.route('**/api/v1/diagnostics/connections/*/validations', (route) =>
    route.fulfill({ json: validationHistory }),
  );
}
