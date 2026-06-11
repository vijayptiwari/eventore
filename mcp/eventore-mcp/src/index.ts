#!/usr/bin/env node
import { createEventoreMcpServer } from './server.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { StreamableHTTPServerTransport } from '@modelcontextprotocol/sdk/server/streamableHttp.js';
import express from 'express';
import { randomUUID } from 'node:crypto';

const apiBaseUrl =
  process.env.EVENTORE_API_URL ?? 'http://localhost:8080/api/v1';
const eventoreApiToken = process.env.EVENTORE_API_TOKEN?.trim() || undefined;
const transport = (process.env.MCP_TRANSPORT ?? 'stdio').toLowerCase();
const port = parseInt(process.env.MCP_PORT ?? '3100', 10);
const authToken = process.env.MCP_AUTH_TOKEN;

function authMiddleware(
  req: express.Request,
  res: express.Response,
  next: express.NextFunction,
): void {
  if (!authToken) {
    next();
    return;
  }
  const header = req.headers.authorization;
  if (header === `Bearer ${authToken}`) {
    next();
    return;
  }
  res.status(401).json({ error: 'Unauthorized' });
}

async function main(): Promise<void> {
  const server = createEventoreMcpServer(apiBaseUrl, eventoreApiToken);

  if (transport === 'http') {
    const app = express();
    app.use(express.json());
    app.get('/health', (_req, res) => {
      res.json({ status: 'ok', eventoreApi: apiBaseUrl });
    });

    const transports = new Map<string, StreamableHTTPServerTransport>();

    app.post('/mcp', authMiddleware, async (req, res) => {
      const sessionId = (req.headers['mcp-session-id'] as string) ?? randomUUID();
      let httpTransport = transports.get(sessionId);
      if (!httpTransport) {
        httpTransport = new StreamableHTTPServerTransport({
          sessionIdGenerator: () => sessionId,
        });
        transports.set(sessionId, httpTransport);
        await server.connect(httpTransport);
      }
      await httpTransport.handleRequest(req, res, req.body);
    });

    app.get('/mcp', authMiddleware, async (req, res) => {
      const sessionId = req.headers['mcp-session-id'] as string;
      const httpTransport = sessionId ? transports.get(sessionId) : undefined;
      if (!httpTransport) {
        res.status(400).send('Invalid or missing mcp-session-id');
        return;
      }
      await httpTransport.handleRequest(req, res);
    });

    app.listen(port, () => {
      console.error(`Eventore MCP (HTTP) listening on :${port}, Eventore API=${apiBaseUrl}`);
    });
  } else {
    const stdioTransport = new StdioServerTransport();
    await server.connect(stdioTransport);
    console.error(`Eventore MCP (stdio) connected to ${apiBaseUrl}`);
  }
}

main().catch((err) => {
  console.error('Fatal:', err);
  process.exit(1);
});
