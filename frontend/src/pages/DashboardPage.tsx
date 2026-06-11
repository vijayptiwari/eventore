import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import { useControlPlane } from '../hooks/useControlPlane';

function truncate(text: string | null | undefined, max = 48): string {
  if (!text) return '';
  return text.length > max ? `${text.slice(0, max)}…` : text;
}

export default function DashboardPage() {
  const { config, plane, connectionProtocols, inspectProtocols, adminProtocols, isLoading } =
    useControlPlane();

  const { data: subscriptions = [], isLoading: subsLoading } = useQuery({
    queryKey: ['diagnostics', 'subscriptions'],
    queryFn: () => api.diagnosticsSubscriptions(),
    refetchInterval: 10_000,
  });

  const { data: connections = [] } = useQuery({
    queryKey: ['connections'],
    queryFn: () => api.listConnections(),
  });

  const { data: validationSummary = [] } = useQuery({
    queryKey: ['diagnostics', 'validations', connections.map((c) => c.id).join(',')],
    queryFn: async () => {
      const rows = await Promise.all(
        connections
          .filter((c) => c.id)
          .map(async (c) => {
            const history = await api.diagnosticsValidations(c.id!);
            const last = history[history.length - 1];
            return { connectionId: c.id!, name: c.name, last };
          }),
      );
      return rows.filter((r) => r.last);
    },
    enabled: connections.length > 0,
    refetchInterval: 10_000,
  });

  if (isLoading) {
    return <p>Loading...</p>;
  }

  return (
    <div>
      <h1>Dashboard</h1>
      <div className="dashboard-grid">
        <div className="card">
          <p>
            Deployment mode: <span className="tag">{config?.deploymentMode}</span>
          </p>
          <p>
            Control plane revision: <span className="tag">{plane?.revision ?? 0}</span>
          </p>
          <p>Connection protocols (data plane, via control plane):</p>
          <p>
            {connectionProtocols.map((p) => (
              <span key={p} className="tag" style={{ marginRight: '0.5rem' }}>
                {p}
              </span>
            ))}
          </p>
          {inspectProtocols.length > 0 && (
            <>
              <p>Inspect enabled:</p>
              <p>
                {inspectProtocols.map((p) => (
                  <span key={p} className="tag" style={{ marginRight: '0.5rem' }}>
                    {p}
                  </span>
                ))}
              </p>
            </>
          )}
          {adminProtocols.length > 0 && (
            <>
              <p>Admin APIs:</p>
              <p>
                {adminProtocols.map((p) => (
                  <span key={p} className="tag" style={{ marginRight: '0.5rem' }}>
                    {p}
                  </span>
                ))}
              </p>
            </>
          )}
          <p>Allowed actions: {config?.allowedActions?.join(', ')}</p>
          {validationSummary.length > 0 && (
            <>
              <h3>Connection validation</h3>
              <ul className="validation-summary">
                {validationSummary.map((row) => (
                  <li key={row.connectionId}>
                    <Link to="/connections">{row.name}</Link>:{' '}
                    <span className={`tag status-${row.last.status.toLowerCase()}`}>
                      {row.last.status}
                    </span>{' '}
                    <span className="inspector-meta">{row.last.timestamp}</span>
                  </li>
                ))}
              </ul>
            </>
          )}
        </div>
        <div className="card subscription-health-card">
          <h2>Subscription health</h2>
          {subsLoading ? (
            <p>Loading subscriptions…</p>
          ) : subscriptions.length === 0 ? (
            <p className="empty-state">
              No active subscriptions — open Live Stream or Browse to start.
            </p>
          ) : (
            <table className="diagnostics-table">
              <thead>
                <tr>
                  <th>Connection</th>
                  <th>Protocol</th>
                  <th>Destination</th>
                  <th>Transport</th>
                  <th>Messages</th>
                  <th>Status</th>
                  <th>Last error</th>
                </tr>
              </thead>
              <tbody>
                {subscriptions.map((row) => (
                  <tr
                    key={row.subscriptionId}
                    className={row.lastError ? 'diagnostics-row-error' : undefined}
                  >
                    <td>{row.connectionName || row.connectionId}</td>
                    <td>{row.protocol}</td>
                    <td>{row.destination}</td>
                    <td>{row.transport}</td>
                    <td>{row.messageCount}</td>
                    <td>
                      <span className={`tag status-${row.status.toLowerCase().replace('_', '-')}`}>
                        {row.status}
                      </span>
                    </td>
                    <td title={row.lastError ?? undefined}>{truncate(row.lastError)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
        <div className="card">
          <h2>Architecture</h2>
          <p>
            <strong>Control plane</strong> registers stream providers and drives what the UI shows.
            <strong> Data plane</strong> handles connections, publish, subscribe, and inspect against
            brokers — only for registered protocols.
          </p>
          <p className="inspector-meta">
            Control API: <code>/api/v1/control/plane</code> · Data API:{' '}
            <code>/api/v1/connections/...</code>
          </p>
        </div>
        <div className="card">
          <h2>Getting started</h2>
          <ol>
            <li>
              <Link to="/connections?wizard=1">Create a connection</Link> to your broker (guided
              wizard).
            </li>
            <li>Browse topics or queues and add multiple streams to the side panel.</li>
            <li>Streams persist in cookies — close the tab and reopen to restore them.</li>
          </ol>
        </div>
      </div>
    </div>
  );
}
