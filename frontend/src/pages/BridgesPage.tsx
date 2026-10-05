import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { api } from '../api/client';
import BridgeWizardDialog from '../components/BridgeWizardDialog';
import type {
  ConnectionProfile,
  ProtocolType,
  ReplicationBridge,
  ReplicationTestRequest,
  ReplicationTestResult,
} from '../api/types';

function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(1))} ${sizes[i]}`;
}

function formatDate(iso?: string): string {
  if (!iso) return 'Never';
  try {
    const d = new Date(iso);
    return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }) + ' ' + d.toLocaleDateString();
  } catch {
    return iso;
  }
}

function getProtocolClass(protocol?: ProtocolType): string {
  switch (protocol) {
    case 'KAFKA': return 'kafka';
    case 'RABBITMQ': return 'rabbitmq';
    case 'MQTT': return 'mqtt';
    case 'PULSAR': return 'pulsar';
    case 'JMS': return 'jms';
    case 'KINESIS': return 'kinesis';
    case 'GCP_PUBSUB': return 'gcp_pubsub';
    case 'AZURE_SERVICE_BUS': return 'azure_service_bus';
    default: return 'kafka';
  }
}

export default function BridgesPage() {
  const queryClient = useQueryClient();

  const {
    data: bridges = [],
    isLoading: bridgesLoading,
    error: bridgesError,
    refetch,
  } = useQuery<ReplicationBridge[]>({
    queryKey: ['bridges'],
    queryFn: api.listBridges,
    refetchInterval: 4000,
  });

  const { data: connections = [] } = useQuery<ConnectionProfile[]>({
    queryKey: ['connections'],
    queryFn: api.listConnections,
  });

  const connMap = useMemo(() => {
    const map = new Map<string, ConnectionProfile>();
    for (const c of connections) {
      if (c.id) map.set(c.id, c);
    }
    return map;
  }, [connections]);

  // Dialog State
  const [wizardOpen, setWizardOpen] = useState(false);
  const [editingBridge, setEditingBridge] = useState<ReplicationBridge | null>(null);

  // Error State for Bridge Actions
  const [actionError, setActionError] = useState<string | null>(null);

  // Tester State
  const [testerOpen, setTesterOpen] = useState(false);
  const [testPayload, setTestPayload] = useState('{\n  "orderId": "ORD-9821",\n  "amount": 250.00,\n  "region": "US-WEST",\n  "status": "COMPLETED"\n}');
  const [testHeadersText, setTestHeadersText] = useState('{\n  "x-origin": "mobile-app",\n  "trace-id": "tr-44123"\n}');
  const [testFilter, setTestFilter] = useState('.*"status":\\s*"COMPLETED".*');
  const [testTransformText, setTestTransformText] = useState('{\n  "rename:x-origin": "x-source-client",\n  "x-environment": "production"\n}');
  const [testLoopPrev, setTestLoopPrev] = useState(true);
  const [testResult, setTestResult] = useState<ReplicationTestResult | null>(null);
  const [testError, setTestError] = useState<string | null>(null);

  // Lifecycle Mutations
  const startMutation = useMutation({
    mutationFn: (id: string) => api.startBridge(id),
    onSuccess: () => {
      setActionError(null);
      void queryClient.invalidateQueries({ queryKey: ['bridges'] });
    },
    onError: (err: unknown) => {
      setActionError(err instanceof Error ? err.message : String(err));
    },
  });

  const stopMutation = useMutation({
    mutationFn: (id: string) => api.stopBridge(id),
    onSuccess: () => {
      setActionError(null);
      void queryClient.invalidateQueries({ queryKey: ['bridges'] });
    },
    onError: (err: unknown) => {
      setActionError(err instanceof Error ? err.message : String(err));
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => api.deleteBridge(id),
    onSuccess: () => {
      setActionError(null);
      void queryClient.invalidateQueries({ queryKey: ['bridges'] });
    },
    onError: (err: unknown) => {
      setActionError(err instanceof Error ? err.message : String(err));
    },
  });

  const testMutation = useMutation({
    mutationFn: async () => {
      setTestError(null);
      let headers: Record<string, string> | undefined;
      let headerTransform: Record<string, string> | undefined;

      try {
        if (testHeadersText.trim()) {
          headers = JSON.parse(testHeadersText);
        }
      } catch {
        throw new Error('Test Headers must be valid JSON object');
      }

      try {
        if (testTransformText.trim()) {
          headerTransform = JSON.parse(testTransformText);
        }
      } catch {
        throw new Error('Header Transform Rules must be valid JSON object');
      }

      const req: ReplicationTestRequest = {
        payload: testPayload,
        headers,
        payloadFilter: testFilter.trim() || undefined,
        headerTransform,
        loopPrevention: testLoopPrev,
      };

      return api.testBridge(req);
    },
    onSuccess: (data) => setTestResult(data),
    onError: (err: unknown) => setTestError(err instanceof Error ? err.message : String(err)),
  });

  // KPI Calculations
  const totalPipelines = bridges.length;
  const activePipelines = bridges.filter((b) => b.stats.state === 'RUNNING').length;
  const totalMessages = bridges.reduce((acc, b) => acc + (b.stats.totalReplicated || 0), 0);
  const totalErrors = bridges.reduce((acc, b) => acc + (b.stats.errorsCount || 0), 0);
  const totalBytes = bridges.reduce((acc, b) => acc + (b.stats.bytesReplicated || 0), 0);

  return (
    <div className="bridges-page">
      {/* Header */}
      <div className="bridges-header-row">
        <div className="bridges-header-title">
          <h1>Cross-Broker Replication Engine</h1>
          <p>Real-time cross-protocol replication pipelines between Kafka, RabbitMQ, Kinesis, Pulsar, and more.</p>
        </div>
        <div className="bridges-header-actions">
          <button
            type="button"
            className="btn-secondary"
            onClick={() => setTesterOpen((v) => !v)}
          >
            🧪 {testerOpen ? 'Hide Rule Tester' : 'Dry-Run Rule Tester'}
          </button>
          <button
            type="button"
            className="btn-primary"
            onClick={() => {
              setEditingBridge(null);
              setWizardOpen(true);
            }}
          >
            + New Replication Bridge
          </button>
        </div>
      </div>

      {/* KPI Cards */}
      <div className="bridge-kpi-grid">
        <div className="bridge-kpi-card">
          <span className="bridge-kpi-label">Total Pipelines</span>
          <span className="bridge-kpi-val">{totalPipelines}</span>
        </div>
        <div className="bridge-kpi-card">
          <span className="bridge-kpi-label">Active Replicating</span>
          <span className={`bridge-kpi-val ${activePipelines > 0 ? 'active' : ''}`}>{activePipelines}</span>
        </div>
        <div className="bridge-kpi-card">
          <span className="bridge-kpi-label">Replicated Messages</span>
          <span className="bridge-kpi-val">{totalMessages.toLocaleString()}</span>
        </div>
        <div className="bridge-kpi-card">
          <span className="bridge-kpi-label">Replication Volume</span>
          <span className="bridge-kpi-val bytes">{formatBytes(totalBytes)}</span>
        </div>
        <div className="bridge-kpi-card">
          <span className="bridge-kpi-label">Pipeline Errors</span>
          <span className={`bridge-kpi-val ${totalErrors > 0 ? 'error' : ''}`}>{totalErrors}</span>
        </div>
      </div>

      {/* Dry-Run Tester Workbench */}
      {testerOpen && (
        <div className="bridge-tester-box">
          <div className="bridge-tester-box-header">
            <h2>Dry-Run Rule Tester & Transformer Playground</h2>
            <button
              type="button"
              className="btn-secondary"
              onClick={() => setTesterOpen(false)}
              style={{ padding: '0.2rem 0.5rem', fontSize: '0.8rem' }}
            >
              Close
            </button>
          </div>

          {testError && (
            <div className="stream-error" style={{ marginBottom: '1rem', padding: '0.65rem', borderRadius: '6px' }}>
              {testError}
            </div>
          )}

          <div className="bridge-tester-grid">
            {/* Input Workbench */}
            <div className="bridge-tester-form">
              <div>
                <label style={{ display: 'block', fontSize: '0.8rem', color: '#94a3b8', marginBottom: '0.25rem', fontWeight: 600 }}>
                  Sample Message Payload
                </label>
                <textarea
                  style={{ width: '100%', height: '110px', background: '#020617', border: '1px solid #334155', borderRadius: '6px', color: '#f8fafc', fontFamily: 'monospace', fontSize: '0.8rem', padding: '0.5rem' }}
                  value={testPayload}
                  onChange={(e) => setTestPayload(e.target.value)}
                />
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '0.8rem', color: '#94a3b8', marginBottom: '0.25rem', fontWeight: 600 }}>
                  Sample Ingress Headers (JSON)
                </label>
                <textarea
                  style={{ width: '100%', height: '70px', background: '#020617', border: '1px solid #334155', borderRadius: '6px', color: '#f8fafc', fontFamily: 'monospace', fontSize: '0.8rem', padding: '0.5rem' }}
                  value={testHeadersText}
                  onChange={(e) => setTestHeadersText(e.target.value)}
                />
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '0.8rem', color: '#94a3b8', marginBottom: '0.25rem', fontWeight: 600 }}>
                  Payload Regex Filter
                </label>
                <input
                  type="text"
                  style={{ width: '100%', padding: '0.45rem', background: '#020617', border: '1px solid #334155', borderRadius: '6px', color: '#f8fafc', fontFamily: 'monospace', fontSize: '0.8rem' }}
                  value={testFilter}
                  onChange={(e) => setTestFilter(e.target.value)}
                />
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '0.8rem', color: '#94a3b8', marginBottom: '0.25rem', fontWeight: 600 }}>
                  Header Transform Rules (JSON)
                </label>
                <textarea
                  style={{ width: '100%', height: '70px', background: '#020617', border: '1px solid #334155', borderRadius: '6px', color: '#f8fafc', fontFamily: 'monospace', fontSize: '0.8rem', padding: '0.5rem' }}
                  value={testTransformText}
                  onChange={(e) => setTestTransformText(e.target.value)}
                />
              </div>

              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.8rem', color: '#e2e8f0', cursor: 'pointer' }}>
                  <input
                    type="checkbox"
                    checked={testLoopPrev}
                    onChange={(e) => setTestLoopPrev(e.target.checked)}
                  />
                  <span>Enforce Loop Prevention</span>
                </label>
                <button
                  type="button"
                  className="btn-primary"
                  onClick={() => testMutation.mutate()}
                  disabled={testMutation.isPending}
                >
                  {testMutation.isPending ? 'Testing...' : 'Execute Dry Run'}
                </button>
              </div>
            </div>

            {/* Test Results Output */}
            <div className="bridge-tester-result">
              <div style={{ fontSize: '0.85rem', fontWeight: 700, color: '#f8fafc' }}>
                Simulation Results
              </div>

              {testResult ? (
                <>
                  <div className={`bridge-tester-verdict ${testResult.passedFilter ? 'pass' : 'fail'}`}>
                    <span>Filter Verdict:</span>
                    <strong>{testResult.passedFilter ? '✓ PASSED FILTER' : '✕ DROPPED BY FILTER'}</strong>
                    <span style={{ fontSize: '0.75rem', fontWeight: 400, opacity: 0.85 }}>({testResult.filterReason})</span>
                  </div>

                  {testResult.loopDetected && (
                    <div className="bridge-tester-verdict fail">
                      <span>Loop Detection:</span>
                      <strong>⚠️ ECHO LOOP DETECTED (Message Dropped)</strong>
                    </div>
                  )}

                  <div>
                    <span style={{ fontSize: '0.75rem', textTransform: 'uppercase', color: '#94a3b8', fontWeight: 600 }}>
                      Transformed Headers ({Object.keys(testResult.transformedHeaders || {}).length})
                    </span>
                    <pre className="bridge-tester-code">
                      {JSON.stringify(testResult.transformedHeaders || {}, null, 2)}
                    </pre>
                  </div>

                  <div>
                    <span style={{ fontSize: '0.75rem', textTransform: 'uppercase', color: '#94a3b8', fontWeight: 600 }}>
                      Transformed Payload
                    </span>
                    <pre className="bridge-tester-code">
                      {testResult.transformedPayload}
                    </pre>
                  </div>
                </>
              ) : (
                <div style={{ color: '#64748b', fontSize: '0.85rem', fontStyle: 'italic', margin: 'auto 0', textAlign: 'center' }}>
                  Click &ldquo;Execute Dry Run&rdquo; to test regex payload filtering and header transformations against mock events.
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Error and Notification Banners */}
      {bridgesError && (
        <div
          className="stream-error"
          style={{
            marginBottom: '1.25rem',
            padding: '1rem',
            borderRadius: '8px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: '1rem',
          }}
        >
          <div>
            <strong>⚠️ Unable to load replication bridges:</strong>{' '}
            <span>{bridgesError instanceof Error ? bridgesError.message : String(bridgesError)}</span>
          </div>
          <button
            type="button"
            className="btn-secondary"
            onClick={() => void refetch()}
            style={{ padding: '0.35rem 0.75rem', fontSize: '0.8rem' }}
          >
            Retry
          </button>
        </div>
      )}

      {actionError && (
        <div
          className="stream-error"
          style={{
            marginBottom: '1.25rem',
            padding: '1rem',
            borderRadius: '8px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: '1rem',
          }}
        >
          <div>
            <strong>⚠️ Pipeline Action Failed:</strong> <span>{actionError}</span>
          </div>
          <button
            type="button"
            className="btn-secondary"
            onClick={() => setActionError(null)}
            style={{ padding: '0.25rem 0.5rem', fontSize: '0.8rem' }}
          >
            Dismiss
          </button>
        </div>
      )}

      {connections.length === 0 && !bridgesLoading && (
        <div
          style={{
            background: '#1e293b',
            border: '1px solid #334155',
            borderRadius: '8px',
            padding: '0.85rem 1.25rem',
            marginBottom: '1.25rem',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            color: '#94a3b8',
            fontSize: '0.85rem',
          }}
        >
          <span>
            ℹ️ <strong>No broker connections found:</strong> Replication bridges require source and target broker connections to be established first.
          </span>
          <a
            href="/connections"
            className="btn-secondary"
            style={{ padding: '0.3rem 0.7rem', fontSize: '0.8rem', textDecoration: 'none', color: '#f8fafc' }}
          >
            Manage Connections →
          </a>
        </div>
      )}

      {/* Pipeline Cards */}
      {bridgesLoading ? (
        <p style={{ color: '#94a3b8' }}>Loading replication pipelines...</p>
      ) : bridges.length === 0 ? (
        <div
          className="card"
          style={{
            textAlign: 'center',
            padding: '3rem 1.5rem',
            background: '#111827',
            border: '1px dashed #334155',
            borderRadius: '12px',
          }}
        >
          <div style={{ fontSize: '2.5rem', marginBottom: '0.75rem' }}>🌉</div>
          <h3 style={{ margin: '0 0 0.5rem 0', color: '#f8fafc' }}>No Replication Bridges Configured</h3>
          <p style={{ margin: '0 0 1.5rem 0', color: '#94a3b8', maxWidth: '500px', marginLeft: 'auto', marginRight: 'auto' }}>
            Connect disparate message brokers (e.g. replicate from Kafka topic to RabbitMQ queue) with automatic header transforms and loop prevention.
          </p>
          <button
            type="button"
            className="btn-primary"
            onClick={() => {
              setEditingBridge(null);
              setWizardOpen(true);
            }}
          >
            + Create Your First Bridge
          </button>
        </div>
      ) : (
        <div className="bridge-cards-container">
          {bridges.map((bridge) => {
            const srcConn = connMap.get(bridge.sourceConnectionId);
            const tgtConn = connMap.get(bridge.targetConnectionId);
            const isRunning = bridge.stats.state === 'RUNNING';
            const ruleCount = Object.keys(bridge.headerTransform || {}).length;

            return (
              <div
                key={bridge.id}
                className={`bridge-card ${isRunning ? 'running' : bridge.stats.state === 'ERROR' ? 'error' : ''}`}
              >
                {/* Header */}
                <div className="bridge-card-header">
                  <div className="bridge-card-title-group">
                    <h3 className="bridge-card-title">{bridge.name}</h3>
                    <span className={`bridge-state-badge ${bridge.stats.state.toLowerCase()}`}>
                      {bridge.stats.state}
                    </span>
                  </div>
                  <span style={{ fontSize: '0.75rem', color: '#64748b' }}>
                    Created {formatDate(bridge.createdAt)}
                  </span>
                </div>

                {/* Visual Route Flow */}
                <div className="bridge-route-flow">
                  {/* Source Node */}
                  <div className="bridge-endpoint-node">
                    <span className="bridge-node-label">Ingress Source</span>
                    <div className="bridge-node-conn">
                      <span className={`bridge-protocol-pill ${getProtocolClass(srcConn?.protocol)}`}>
                        {srcConn?.protocol || 'BROKER'}
                      </span>
                      <span>{srcConn?.name || bridge.sourceConnectionId}</span>
                    </div>
                    <span className="bridge-node-dest">{bridge.sourceDestination}</span>
                  </div>

                  {/* Flow Arrow & Rules Chips */}
                  <div className="bridge-flow-arrow">
                    <div className="bridge-flow-arrow-line">
                      <span>─────</span>
                      <span>▶</span>
                    </div>
                    <div style={{ display: 'flex', gap: '0.35rem', flexWrap: 'wrap', justifyContent: 'center' }}>
                      {ruleCount > 0 && (
                        <span className="bridge-flow-arrow-pill">{ruleCount} {ruleCount === 1 ? 'Rule' : 'Rules'}</span>
                      )}
                      {bridge.payloadFilter && (
                        <span className="bridge-flow-arrow-pill" title={`Filter: ${bridge.payloadFilter}`}>Regex Filter</span>
                      )}
                      <span className="bridge-flow-arrow-pill">
                        {bridge.loopPrevention ? '🛡️ Loop Prev' : 'No Loop Prev'}
                      </span>
                    </div>
                  </div>

                  {/* Target Node */}
                  <div className="bridge-endpoint-node target">
                    <span className="bridge-node-label">Egress Target</span>
                    <div className="bridge-node-conn">
                      <span>{tgtConn?.name || bridge.targetConnectionId}</span>
                      <span className={`bridge-protocol-pill ${getProtocolClass(tgtConn?.protocol)}`}>
                        {tgtConn?.protocol || 'BROKER'}
                      </span>
                    </div>
                    <span className="bridge-node-dest">{bridge.targetDestination}</span>
                  </div>
                </div>

                {/* Metrics Grid */}
                <div className="bridge-metrics-grid">
                  <div className="bridge-metric-item">
                    <span className="bridge-metric-title">Replicated Messages</span>
                    <span className="bridge-metric-number highlight">
                      {bridge.stats.totalReplicated.toLocaleString()}
                    </span>
                  </div>
                  <div className="bridge-metric-item">
                    <span className="bridge-metric-title">Data Volume</span>
                    <span className="bridge-metric-number">
                      {formatBytes(bridge.stats.bytesReplicated)}
                    </span>
                  </div>
                  <div className="bridge-metric-item">
                    <span className="bridge-metric-title">Error Count</span>
                    <span className={`bridge-metric-number ${bridge.stats.errorsCount > 0 ? 'danger' : ''}`}>
                      {bridge.stats.errorsCount}
                    </span>
                  </div>
                  <div className="bridge-metric-item">
                    <span className="bridge-metric-title">Last Activity</span>
                    <span className="bridge-metric-number" style={{ fontSize: '0.85rem' }}>
                      {formatDate(bridge.stats.lastReplicatedAt)}
                    </span>
                  </div>
                </div>

                {/* Last Error Banner if any */}
                {bridge.stats.lastError && (
                  <div className="stream-error" style={{ padding: '0.5rem 0.75rem', borderRadius: '6px', fontSize: '0.8rem' }}>
                    <strong>Last Pipeline Error:</strong> {bridge.stats.lastError}
                  </div>
                )}

                {/* Rules & Filters Summary */}
                {(ruleCount > 0 || bridge.payloadFilter) && (
                  <div className="bridge-rules-summary">
                    <span style={{ fontSize: '0.75rem', textTransform: 'uppercase', color: '#64748b', fontWeight: 600 }}>
                      Active Transforms:
                    </span>
                    {bridge.payloadFilter && (
                      <span className="bridge-rule-chip">Regex: {bridge.payloadFilter}</span>
                    )}
                    {bridge.headerTransform && Object.entries(bridge.headerTransform).map(([k, v]) => (
                      <span key={k} className="bridge-rule-chip">
                        {k} ➔ {v || '(dropped)'}
                      </span>
                    ))}
                  </div>
                )}

                {/* Card Actions */}
                <div className="bridge-card-actions">
                  {isRunning ? (
                    <button
                      type="button"
                      className="btn-secondary"
                      onClick={() => stopMutation.mutate(bridge.id)}
                      disabled={stopMutation.isPending}
                    >
                      ⏸ Stop Pipeline
                    </button>
                  ) : (
                    <button
                      type="button"
                      className="btn-primary"
                      onClick={() => startMutation.mutate(bridge.id)}
                      disabled={startMutation.isPending}
                    >
                      ▶ Start Pipeline
                    </button>
                  )}
                  <button
                    type="button"
                    className="btn-secondary"
                    onClick={() => {
                      setEditingBridge(bridge);
                      setWizardOpen(true);
                    }}
                  >
                    Edit
                  </button>
                  <button
                    type="button"
                    className="btn-danger"
                    onClick={() => {
                      if (window.confirm(`Are you sure you want to delete replication pipeline "${bridge.name}"?`)) {
                        deleteMutation.mutate(bridge.id);
                      }
                    }}
                    disabled={deleteMutation.isPending}
                  >
                    Delete
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Creation / Edit Wizard Dialog */}
      <BridgeWizardDialog
        open={wizardOpen}
        onClose={() => {
          setWizardOpen(false);
          setEditingBridge(null);
        }}
        initialBridge={editingBridge}
        onSaved={() => {
          void refetch();
        }}
      />
    </div>
  );
}
