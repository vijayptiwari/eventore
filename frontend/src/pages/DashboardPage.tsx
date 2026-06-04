import { useControlPlane } from '../hooks/useControlPlane';

export default function DashboardPage() {
  const { config, plane, connectionProtocols, inspectProtocols, adminProtocols, isLoading } =
    useControlPlane();

  if (isLoading) {
    return <p>Loading...</p>;
  }

  return (
    <div>
      <h1>Dashboard</h1>
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
          <li>Create a connection to your broker under Connections.</li>
          <li>Browse topics or queues and add multiple streams to the side panel.</li>
          <li>Streams persist in cookies — close the tab and reopen to restore them.</li>
        </ol>
      </div>
    </div>
  );
}
