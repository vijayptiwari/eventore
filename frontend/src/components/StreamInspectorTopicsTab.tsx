import type { UseMutationResult } from '@tanstack/react-query';
import type { ProtocolType, UnifiedMessage } from '../api/types';
import ExportResultActions from './ExportResultActions';
import MessagePayloadViewer from './MessagePayloadViewer';

interface TopicRow {
  name: string;
  partitionCount?: number;
}

interface Props {
  connectionId: string;
  connectionName: string;
  protocol: ProtocolType;
  topicFilter: string;
  onTopicFilterChange: (value: string) => void;
  topics: TopicRow[] | undefined;
  detailsTopic: string;
  onDetailsTopicChange: (value: string) => void;
  topicDetail: unknown;
  canDump: boolean;
  dumpStartAt: 'latest' | 'earliest';
  onDumpStartAtChange: (value: 'latest' | 'earliest') => void;
  dumpMutation: UseMutationResult<UnifiedMessage[], Error, void, unknown>;
}

export default function StreamInspectorTopicsTab({
  connectionId,
  connectionName,
  protocol,
  topicFilter,
  onTopicFilterChange,
  topics,
  detailsTopic,
  onDetailsTopicChange,
  topicDetail,
  canDump,
  dumpStartAt,
  onDumpStartAtChange,
  dumpMutation,
}: Props) {
  return (
    <div className="card">
      <div className="form-row">
        <label>Filter</label>
        <input value={topicFilter} onChange={(e) => onTopicFilterChange(e.target.value)} />
      </div>
      <ExportResultActions
        filenameBase={`topics_${connectionName}`}
        jsonData={topics}
        meta={{ connectionId, protocol, filter: topicFilter }}
      />
      <table>
        <thead>
          <tr>
            <th>Name</th>
            <th>Partitions</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {topics?.map((t) => (
            <tr key={t.name}>
              <td>{t.name}</td>
              <td>{t.partitionCount ?? '—'}</td>
              <td>
                <button type="button" className="secondary" onClick={() => onDetailsTopicChange(t.name)}>
                  Details
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {topicDetail != null ? (
        <div className="topic-matrix-section" data-testid="topic-matrix-section">
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.75rem' }}>
            <h4 style={{ margin: 0, color: '#f1f5f9' }}>
              Topic Topology & Partition Distribution: <code style={{ color: '#38bdf8' }}>{detailsTopic}</code>
            </h4>
            <ExportResultActions
              filenameBase={`topic-detail_${detailsTopic}`}
              jsonData={topicDetail}
              meta={{ connectionId, protocol }}
            />
          </div>

          {/* Type-safe check for structured topic detail */}
          {typeof topicDetail === 'object' && topicDetail !== null && 'partitions' in topicDetail && Array.isArray((topicDetail as { partitions: unknown[] }).partitions) ? (
            (() => {
              const detail = topicDetail as {
                name?: string;
                partitionCount?: number;
                replicationFactor?: number;
                partitions: { partition: number; leader: number; replicas: number[]; isr: number[] }[];
                config?: Record<string, string>;
              };

              const totalPartitions = detail.partitions.length;
              const underReplicated = detail.partitions.filter((p) => p.isr.length < p.replicas.length);
              const isHealthy = underReplicated.length === 0;

              return (
                <>
                  <div className="topology-kpi-grid" data-testid="topic-topology-kpi-grid">
                    <div className="topology-kpi-card" data-testid="kpi-topic-partitions">
                      <span className="topology-kpi-label">Partitions</span>
                      <span className="topology-kpi-val">{totalPartitions}</span>
                      <span className="topology-kpi-sub">Total topic partitions</span>
                    </div>

                    <div className="topology-kpi-card" data-testid="kpi-topic-replication">
                      <span className="topology-kpi-label">Replication Factor</span>
                      <span className="topology-kpi-val">{detail.replicationFactor ?? '—'}</span>
                      <span className="topology-kpi-sub">Target replica count</span>
                    </div>

                    <div className="topology-kpi-card" data-testid="kpi-isr-health">
                      <span className="topology-kpi-label">Replication Health</span>
                      <div>
                        <span className={`lag-status-badge ${isHealthy ? 'lag-status-healthy' : 'lag-status-critical'}`}>
                          {isHealthy ? '● 100% IN-SYNC' : `▲ ${underReplicated.length} UNDER-REPLICATED`}
                        </span>
                      </div>
                      <span className="topology-kpi-sub">In-sync replica status</span>
                    </div>
                  </div>

                  <table data-testid="topic-partitions-table">
                    <thead>
                      <tr>
                        <th>Partition</th>
                        <th>Leader Broker</th>
                        <th>Replicas</th>
                        <th>In-Sync Replicas (ISR)</th>
                        <th>Health Status</th>
                      </tr>
                    </thead>
                    <tbody>
                      {detail.partitions.map((part) => {
                        const inSync = part.isr.length >= part.replicas.length;
                        return (
                          <tr key={part.partition}>
                            <td>
                              <strong>P#{part.partition}</strong>
                            </td>
                            <td>
                              <span style={{ color: '#facc15', fontWeight: 600 }}>
                                🖥️ Broker #{part.leader}
                              </span>
                            </td>
                            <td>
                              <div style={{ display: 'flex', gap: '0.25rem', flexWrap: 'wrap' }}>
                                {part.replicas.map((r) => (
                                  <span key={r} className="feature-chip" style={{ margin: 0 }}>
                                    #{r}
                                  </span>
                                ))}
                              </div>
                            </td>
                            <td>
                              <div style={{ display: 'flex', gap: '0.25rem', flexWrap: 'wrap' }}>
                                {part.isr.map((r) => (
                                  <span
                                    key={r}
                                    className="feature-chip"
                                    style={{ margin: 0, borderColor: inSync ? '#22c55e' : '#f97316' }}
                                  >
                                    #{r}
                                  </span>
                                ))}
                              </div>
                            </td>
                            <td>
                              <span className={`partition-health-tag ${inSync ? 'insync' : 'under-replicated'}`}>
                                {inSync ? '✓ In-Sync' : '⚠️ Under-Replicated'}
                              </span>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>

                  {detail.config && Object.keys(detail.config).length > 0 && (
                    <details style={{ marginTop: '1rem', cursor: 'pointer' }}>
                      <summary style={{ fontSize: '0.8rem', color: '#94a3b8' }}>
                        View Topic Configuration ({Object.keys(detail.config).length} settings)
                      </summary>
                      <table style={{ marginTop: '0.5rem', fontSize: '0.78rem' }}>
                        <thead>
                          <tr>
                            <th>Configuration Key</th>
                            <th>Value</th>
                          </tr>
                        </thead>
                        <tbody>
                          {Object.entries(detail.config).map(([k, v]) => (
                            <tr key={k}>
                              <td><code>{k}</code></td>
                              <td><code style={{ color: '#7dd3fc' }}>{v}</code></td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </details>
                  )}
                </>
              );
            })()
          ) : (
            <pre className="inspector-pre">{JSON.stringify(topicDetail, null, 2)}</pre>
          )}
        </div>
      ) : null}
      {canDump && (
        <>
          <hr />
          <h4>{protocol === 'RABBITMQ' ? 'Queue message dump' : 'Topic message dump'}</h4>
          <p className="inspector-meta">
            Sample up to 200 messages from all partitions (no payload/key filter). Export after dump.
          </p>
          <div className="form-row">
            <label>Topic</label>
            <input value={detailsTopic} onChange={(e) => onDetailsTopicChange(e.target.value)} />
          </div>
          <div className="form-row">
            <label>Start position</label>
            <select
              value={dumpStartAt}
              onChange={(e) => onDumpStartAtChange(e.target.value as 'latest' | 'earliest')}
            >
              <option value="latest">Latest (tail sample)</option>
              <option value="earliest">Earliest (head sample)</option>
            </select>
          </div>
          <button
            type="button"
            disabled={dumpMutation.isPending || !detailsTopic}
            onClick={() => dumpMutation.mutate()}
          >
            Dump topic messages
          </button>
          {dumpMutation.isError && (
            <p className="stream-error">{String(dumpMutation.error)}</p>
          )}
          <ExportResultActions
            filenameBase={`topic-dump_${detailsTopic}`}
            messages={dumpMutation.data}
            meta={{
              connectionId,
              topic: detailsTopic,
              startAt: dumpStartAt,
              maxMessages: 200,
            }}
          />
          {dumpMutation.data && dumpMutation.data.length > 0 && (
            <table>
              <thead>
                <tr>
                  <th>Time</th>
                  <th>Partition</th>
                  <th>Offset</th>
                  <th>Key</th>
                  <th>Payload</th>
                </tr>
              </thead>
              <tbody>
                {dumpMutation.data.map((m) => (
                  <tr key={m.id}>
                    <td>{new Date(m.timestamp).toLocaleTimeString()}</td>
                    <td>{m.headers?.partition}</td>
                    <td>{m.headers?.offset}</td>
                    <td>{m.headers?.key ?? '—'}</td>
                    <td className="message-payload">
                      <MessagePayloadViewer payload={m.payload} headers={m.headers} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </>
      )}
    </div>
  );
}
