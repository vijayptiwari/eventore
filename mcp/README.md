# Eventore MCP layer

Optional Node package for AI agent integration via the [Model Context Protocol](https://modelcontextprotocol.io/).

| Package | Path | Role |
|---------|------|------|
| `@eventore/mcp-server` | `eventore-mcp/` | MCP tools, resources, prompts → Eventore REST/SSE |

## Planes

- **Control plane** — `eventore_get_control_plane`, provider register/deregister (no broker I/O)
- **Data plane** — connections, publish, consume samples, inspect, Kafka admin

## Deploy

- **Local:** stdio transport + Cursor (`cursor-mcp.json.example`)
- **Kubernetes:** `docker/Dockerfile.mcp`, Helm chart `deploy/helm/eventore-mcp`
- **Published:** `ghcr.io/vijayptiwari/eventore-mcp`

See `eventore-mcp/README.md` and the [product guide — MCP](https://vijayptiwari.github.io/eventore/guide/mcp.html).
