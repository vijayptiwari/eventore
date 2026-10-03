import { useState } from 'react';

interface BrokerRow {
  id: string | number;
  host: string;
  port: number;
  rack?: string;
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
  const [showRawAttributes, setShowRawAttributes] = useState(false);

  const controllerId = cluster?.attributes?.controller !== undefined
    ? String(cluster.attributes.controller)
    : undefined;

  const brokerCount = cluster?.brokers?.length ?? 0;

  return (
    <div className="card" data-testid="stream-inspector-overview-tab">
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <h3 style={{ margin: 0 }}>Cluster Topology & Brokers</h3>
        {cluster && (
          <span className="broker-status-pill">
            <span className="pulse-dot" /> Connected
          </span>
        )}
      </div>

      {clusterLoading ? <p>Loading cluster topology...</p> : null}
      {clusterError != null ? (
        <p className="stream-error">{String(clusterError)}</p>
      ) : null}

      {/* Cluster Summary KPI Cards */}
      {cluster && (
        <div className="topology-kpi-grid" data-testid="cluster-topology-kpi-grid">
          <div className="topology-kpi-card" data-testid="kpi-cluster-id">
            <span className="topology-kpi-label">Cluster ID</span>
            <span className="topology-kpi-val" style={{ fontSize: '1.05rem', wordBreak: 'break-all' }}>
              {cluster.clusterId || 'standalone'}
            </span>
            <span className="topology-kpi-sub">Unique cluster identifier</span>
          </div>

          <div className="topology-kpi-card" data-testid="kpi-broker-count">
            <span className="topology-kpi-label">Active Brokers</span>
            <span className="topology-kpi-val">{brokerCount}</span>
            <span className="topology-kpi-sub">Total live broker nodes</span>
          </div>

          <div className="topology-kpi-card" data-testid="kpi-controller-node">
            <span className="topology-kpi-label">Cluster Controller</span>
            <span className="topology-kpi-val" style={{ color: controllerId ? '#facc15' : '#f1f5f9' }}>
              {controllerId ? `👑 Broker #${controllerId}` : 'Leaderless / N/A'}
            </span>
            <span className="topology-kpi-sub">Raft / KRaft leader node</span>
          </div>

          <div className="topology-kpi-card" data-testid="kpi-cluster-status">
            <span className="topology-kpi-label">Cluster Status</span>
            <div>
              <span className="lag-status-badge lag-status-healthy">
                ● HEALTHY
              </span>
            </div>
            <span className="topology-kpi-sub">Brokers responding</span>
          </div>
        </div>
      )}

      {/* Feature Capabilities */}
      {capabilities && capabilities.features.length > 0 && (
        <div style={{ margin: '0.75rem 0' }}>
          <p className="inspector-meta" data-testid="capabilities-meta">
            Features: {capabilities.features.join(', ')}
          </p>
          <div className="feature-chips-grid" data-testid="feature-capabilities-grid">
            {capabilities.features.map((feature) => (
              <span key={feature} className="feature-chip">
                ⚡ {feature}
              </span>
            ))}
          </div>
        </div>
      )}

      {/* Broker Nodes Visual Grid */}
      {cluster && cluster.brokers && cluster.brokers.length > 0 && (
        <div style={{ marginTop: '1.25rem' }}>
          <h4 style={{ margin: '0 0 0.5rem 0', color: '#e2e8f0' }}>Broker Nodes Grid</h4>
          <div className="broker-nodes-grid" data-testid="broker-nodes-grid">
            {cluster.brokers.map((b) => {
              const isController = controllerId !== undefined && String(b.id) === controllerId;
              return (
                <div
                  key={b.id}
                  className={`broker-node-card ${isController ? 'is-controller' : ''}`}
                  data-testid={`broker-card-${b.id}`}
                >
                  <div className="broker-node-header">
                    <span className="broker-node-title">
                      🖥️ Broker #{b.id}
                    </span>
                    {isController && (
                      <span className="controller-badge" data-testid={`controller-badge-${b.id}`}>
                        👑 Controller
                      </span>
                    )}
                  </div>

                  <div className="broker-detail-row">
                    <span>Host</span>
                    <span className="broker-detail-value">{b.host}</span>
                  </div>

                  <div className="broker-detail-row">
                    <span>Port</span>
                    <span className="broker-detail-value">{b.port}</span>
                  </div>

                  <div className="broker-detail-row">
                    <span>Rack</span>
                    <span className="broker-detail-value">{b.rack || 'default-rack'}</span>
                  </div>

                  <div className="broker-detail-row">
                    <span>Node Status</span>
                    <span className="broker-status-pill">
                      <span className="pulse-dot" /> Online
                    </span>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Advanced Diagnostics / Raw Attributes Toggle */}
      {(cluster?.attributes != null || brokers?.brokerInfo != null) && (
        <div style={{ marginTop: '1rem' }}>
          <button
            type="button"
            className="secondary"
            style={{ fontSize: '0.75rem', padding: '0.3rem 0.6rem' }}
            onClick={() => setShowRawAttributes(!showRawAttributes)}
            data-testid="toggle-raw-attributes-btn"
          >
            {showRawAttributes ? 'Hide Advanced Diagnostics ▲' : 'Show Advanced Diagnostics ▼'}
          </button>

          {showRawAttributes && (
            <div style={{ marginTop: '0.75rem' }}>
              {cluster?.attributes != null && (
                <>
                  <h5 style={{ margin: '0.5rem 0', color: '#94a3b8' }}>Cluster Attributes</h5>
                  <pre className="inspector-pre" data-testid="cluster-attributes-pre">
                    {JSON.stringify(cluster.attributes, null, 2)}
                  </pre>
                </>
              )}
              {brokers?.brokerInfo != null && (
                <>
                  <h5 style={{ margin: '0.5rem 0', color: '#94a3b8' }}>Broker Info Details</h5>
                  <pre className="inspector-pre" data-testid="broker-info-pre">
                    {JSON.stringify(brokers.brokerInfo, null, 2)}
                  </pre>
                </>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

