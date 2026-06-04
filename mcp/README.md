# Eventore MCP Layer

Optional **separately deployable** MCP integration for messaging buses.

| Component | Path |
|-----------|------|
| MCP server | [`eventore-mcp/`](eventore-mcp/) |
| Docker image | [`../docker/Dockerfile.mcp`](../docker/Dockerfile.mcp) |
| Compose | [`../docker/docker-compose.mcp.yml`](../docker/docker-compose.mcp.yml) |
| Helm chart | [`../deploy/helm/eventore-mcp`](../deploy/helm/eventore-mcp) |

The MCP server talks to the **Eventore backend API** only — not directly to brokers — so ADMIN/DEV/READONLY policies stay enforced.
