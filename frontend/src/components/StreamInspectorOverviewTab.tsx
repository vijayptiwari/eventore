interface BrokerRow {
  id: string | number;
  host: string;
  port: number;
}

interface Props {
  clusterLoading: boolean;
  clusterError: unknown;
  capabilities: { features: string[] } | undefined;
  cluster:
    | {
        clusterId?: string;
        brokers?: BrokerRow[];
        attributes?: Record<string, unknown>;
      }
    | undefined;
  brokers: { brokerInfo?: unknown } | undefined;
}

export default function StreamInspectorOverviewTab({
  clusterLoading,
  clusterError,
  capabilities,
  cluster,
  brokers,
}: Props) {
  return (
    <div className="card">
      <h3>Cluster / broker</h3>
      {clusterLoading ? <p>Loading...</p> : null}
      {clusterError != null ? (
        <p className="stream-error">{String(clusterError)}</p>
      ) : null}
      {capabilities && (
        <p className="inspector-meta">
          Features: {capabilities.features.join(', ')}
        </p>
      )}
      {cluster && (
        <>
          <p>
            <strong>Cluster:</strong> {cluster.clusterId}
          </p>
          <table>
            <thead>
              <tr>
                <th>Broker ID</th>
                <th>Host</th>
                <th>Port</th>
              </tr>
            </thead>
            <tbody>
              {cluster.brokers?.map((b) => (
                <tr key={b.id}>
                  <td>{b.id}</td>
                  <td>{b.host}</td>
                  <td>{b.port}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {cluster.attributes != null ? (
            <pre className="inspector-pre">{JSON.stringify(cluster.attributes, null, 2)}</pre>
          ) : null}
        </>
      )}
      {brokers?.brokerInfo != null ? (
        <pre className="inspector-pre">{JSON.stringify(brokers.brokerInfo, null, 2)}</pre>
      ) : null}
    </div>
  );
}
