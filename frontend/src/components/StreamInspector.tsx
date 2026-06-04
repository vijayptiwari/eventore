import { useMutation, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../api/client';
import type { LiveStreamSession } from '../stream/types';
import ExportResultActions from './ExportResultActions';
import KafkaAdminPanel from './KafkaAdminPanel';
import LiveViewPanel from './LiveViewPanel';
import { hasInspectFeature } from '../utils/inspectFeatures';

type Tab = 'overview' | 'topics' | 'groups' | 'lag' | 'search' | 'admin' | 'live';

interface Props {
  session: LiveStreamSession;
}

export default function StreamInspector({ session }: Props) {
  const [tab, setTab] = useState<Tab>('overview');
  const [topicFilter, setTopicFilter] = useState('');
  const [selectedTopic, setSelectedTopic] = useState(session.destination);
  const [selectedGroup, setSelectedGroup] = useState('');
  const [searchPayload, setSearchPayload] = useState('');
  const [searchKey, setSearchKey] = useState('');
  const [dumpStartAt, setDumpStartAt] = useState<'latest' | 'earliest'>('latest');
  const cid = session.connectionId;
  const protocol = session.protocol;

  const { data: capabilities } = useQuery({
    queryKey: ['inspect-cap', cid],
    queryFn: () => api.inspectCapabilities(cid),
  });

  const { data: cluster, isLoading: clusterLoading, error: clusterError } = useQuery({
    queryKey: ['inspect-cluster', cid],
    queryFn: () => api.inspectCluster(cid),
    enabled: tab === 'overview',
  });

  const { data: brokers } = useQuery({
    queryKey: ['inspect-brokers', cid],
    queryFn: () => api.inspectBrokers(cid),
    enabled: tab === 'overview' && protocol === 'KAFKA',
  });

  const { data: groups } = useQuery({
    queryKey: ['inspect-groups', cid],
    queryFn: () => api.inspectConsumerGroups(cid),
    enabled: tab === 'groups' || tab === 'lag',
  });

  const { data: groupDetail } = useQuery({
    queryKey: ['inspect-group', cid, selectedGroup],
    queryFn: () => api.inspectConsumerGroup(cid, selectedGroup),
    enabled: !!selectedGroup && tab === 'groups',
  });

  const { data: topics } = useQuery({
    queryKey: ['inspect-topics', cid, topicFilter],
    queryFn: () => api.inspectTopics(cid, topicFilter || undefined),
    enabled: tab === 'topics' || tab === 'search',
  });

  const { data: topicDetail } = useQuery({
    queryKey: ['inspect-topic', cid, selectedTopic],
    queryFn: () => api.inspectTopic(cid, selectedTopic),
    enabled: !!selectedTopic && tab === 'topics',
  });

  const feats = capabilities?.features;
  const canSearch =
    hasInspectFeature(feats, 'message-search') ||
    protocol === 'KAFKA' ||
    protocol === 'PULSAR' ||
    protocol === 'RABBITMQ';
  const canLag =
    protocol === 'KAFKA' || hasInspectFeature(feats, 'lag') || hasInspectFeature(feats, 'backlog');
  const canDump = canSearch;

  const { data: lag, refetch: refetchLag } = useQuery({
    queryKey: ['inspect-lag', cid, selectedGroup, selectedTopic],
    queryFn: () => api.inspectLag(cid, selectedGroup, selectedTopic || undefined),
    enabled: tab === 'lag' && !!selectedGroup && canLag,
  });

  const searchMutation = useMutation({
    mutationFn: () =>
      api.inspectSearch(cid, {
        topic: selectedTopic,
        payloadContains: searchPayload || undefined,
        keyContains: searchKey || undefined,
        maxMessages: 50,
        startAt: 'latest',
      }),
  });

  const dumpMutation = useMutation({
    mutationFn: () =>
      api.inspectSearch(cid, {
        topic: selectedTopic,
        maxMessages: 200,
        startAt: dumpStartAt,
      }),
  });

  const tabs: { id: Tab; label: string; show: boolean }[] = [
    { id: 'overview', label: 'Overview', show: true },
    { id: 'topics', label: 'Topics / Queues', show: true },
    {
      id: 'groups',
      label: protocol === 'PULSAR' ? 'Subscriptions' : 'Consumer groups',
      show: protocol === 'KAFKA' || protocol === 'PULSAR' || hasInspectFeature(feats, 'subscription'),
    },
    { id: 'lag', label: protocol === 'PULSAR' ? 'Backlog' : 'Lag', show: canLag },
    { id: 'search', label: 'Message search', show: canSearch },
    { id: 'admin', label: 'Kafka admin', show: protocol === 'KAFKA' },
    { id: 'live', label: 'Live messages', show: true },
  ];

  return (
    <div className="stream-inspector">
      <div className="inspector-tabs">
        {tabs
          .filter((t) => t.show)
          .map((t) => (
            <button
              key={t.id}
              type="button"
              className={tab === t.id ? 'inspector-tab active' : 'inspector-tab'}
              onClick={() => setTab(t.id)}
            >
              {t.label}
            </button>
          ))}
      </div>

      {tab === 'overview' && (
        <div className="card">
          <h3>Cluster / broker</h3>
          {clusterLoading && <p>Loading...</p>}
          {clusterError && <p className="stream-error">{String(clusterError)}</p>}
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
              {cluster.attributes && (
                <pre className="inspector-pre">{JSON.stringify(cluster.attributes, null, 2)}</pre>
              )}
            </>
          )}
          {brokers?.brokerInfo && (
            <pre className="inspector-pre">{JSON.stringify(brokers.brokerInfo, null, 2)}</pre>
          )}
        </div>
      )}

      {tab === 'topics' && (
        <div className="card">
          <div className="form-row">
            <label>Filter</label>
            <input value={topicFilter} onChange={(e) => setTopicFilter(e.target.value)} />
          </div>
          <ExportResultActions
            filenameBase={`topics_${session.connectionName}`}
            jsonData={topics}
            meta={{ connectionId: cid, protocol, filter: topicFilter }}
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
                    <button type="button" className="secondary" onClick={() => setSelectedTopic(t.name)}>
                      Details
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {topicDetail && (
            <>
              <ExportResultActions
                filenameBase={`topic-detail_${selectedTopic}`}
                jsonData={topicDetail}
                meta={{ connectionId: cid, protocol }}
              />
              <pre className="inspector-pre">{JSON.stringify(topicDetail, null, 2)}</pre>
            </>
          )}
          {canDump && (
            <>
              <hr />
              <h4>{protocol === 'RABBITMQ' ? 'Queue message dump' : 'Topic message dump'}</h4>
              <p className="inspector-meta">
                Sample up to 200 messages from all partitions (no payload/key filter). Export after dump.
              </p>
              <div className="form-row">
                <label>Topic</label>
                <input value={selectedTopic} onChange={(e) => setSelectedTopic(e.target.value)} />
              </div>
              <div className="form-row">
                <label>Start position</label>
                <select
                  value={dumpStartAt}
                  onChange={(e) => setDumpStartAt(e.target.value as 'latest' | 'earliest')}
                >
                  <option value="latest">Latest (tail sample)</option>
                  <option value="earliest">Earliest (head sample)</option>
                </select>
              </div>
              <button
                type="button"
                disabled={dumpMutation.isPending || !selectedTopic}
                onClick={() => dumpMutation.mutate()}
              >
                Dump topic messages
              </button>
              {dumpMutation.isError && (
                <p className="stream-error">{String(dumpMutation.error)}</p>
              )}
              <ExportResultActions
                filenameBase={`topic-dump_${selectedTopic}`}
                messages={dumpMutation.data}
                meta={{
                  connectionId: cid,
                  topic: selectedTopic,
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
                        <td className="message-payload">{m.payload}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </>
          )}
        </div>
      )}

      {tab === 'groups' && (
        <div className="card">
          {protocol !== 'KAFKA' && protocol !== 'PULSAR' && (
            <p>Consumer groups are specific to Kafka/Pulsar subscriptions.</p>
          )}
          <table>
            <thead>
              <tr>
                <th>Group</th>
                <th>State</th>
                <th>Members</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {groups?.map((g) => (
                <tr key={g.groupId}>
                  <td>{g.groupId}</td>
                  <td>{g.state}</td>
                  <td>{g.memberCount ?? '—'}</td>
                  <td>
                    <button
                      type="button"
                      className="secondary"
                      onClick={() => setSelectedGroup(g.groupId)}
                    >
                      Details
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {groupDetail && (
            <pre className="inspector-pre">{JSON.stringify(groupDetail, null, 2)}</pre>
          )}
        </div>
      )}

      {tab === 'lag' && canLag && (
        <div className="card">
          <div className="form-grid">
            <div className="form-row">
              <label>Consumer group</label>
              <select value={selectedGroup} onChange={(e) => setSelectedGroup(e.target.value)}>
                <option value="">Select group...</option>
                {groups?.map((g) => (
                  <option key={g.groupId} value={g.groupId}>
                    {g.groupId}
                  </option>
                ))}
              </select>
            </div>
            <div className="form-row">
              <label>Topic filter (optional)</label>
              <input value={selectedTopic} onChange={(e) => setSelectedTopic(e.target.value)} />
            </div>
          </div>
          <button type="button" className="secondary" onClick={() => refetchLag()} disabled={!selectedGroup}>
            Refresh lag
          </button>
          <table>
            <thead>
              <tr>
                <th>Topic</th>
                <th>Partition</th>
                <th>Offset</th>
                <th>Log end</th>
                <th>Lag</th>
              </tr>
            </thead>
            <tbody>
              {lag?.map((row, i) => (
                <tr key={`${row.topic}-${row.partition}-${i}`}>
                  <td>{row.topic}</td>
                  <td>{row.partition ?? '—'}</td>
                  <td>{row.offset ?? '—'}</td>
                  <td>{row.logEndOffset ?? '—'}</td>
                  <td className={row.lag > 0 ? 'lag-warn' : ''}>{row.lag}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {tab === 'search' && canSearch && (
        <div className="card">
          <div className="form-grid">
            <div className="form-row">
              <label>Topic</label>
              <input value={selectedTopic} onChange={(e) => setSelectedTopic(e.target.value)} />
            </div>
            <div className="form-row">
              <label>Payload contains</label>
              <input value={searchPayload} onChange={(e) => setSearchPayload(e.target.value)} />
            </div>
            <div className="form-row">
              <label>Key contains</label>
              <input value={searchKey} onChange={(e) => setSearchKey(e.target.value)} />
            </div>
          </div>
          <button
            type="button"
            disabled={searchMutation.isPending || !selectedTopic}
            onClick={() => searchMutation.mutate()}
          >
            Search (sample)
          </button>
          {searchMutation.isError && (
            <p className="stream-error">{String(searchMutation.error)}</p>
          )}
          <ExportResultActions
            filenameBase={`message-search_${selectedTopic}`}
            messages={searchMutation.data}
            meta={{
              connectionId: cid,
              topic: selectedTopic,
              payloadContains: searchPayload || undefined,
              keyContains: searchKey || undefined,
              maxMessages: 50,
              startAt: 'latest',
            }}
          />
          <table>
            <thead>
              <tr>
                <th>Time</th>
                <th>Partition</th>
                <th>Offset</th>
                <th>Payload</th>
              </tr>
            </thead>
            <tbody>
              {searchMutation.data?.map((m) => (
                <tr key={m.id}>
                  <td>{new Date(m.timestamp).toLocaleTimeString()}</td>
                  <td>{m.headers?.partition}</td>
                  <td>{m.headers?.offset}</td>
                  <td className="message-payload">{m.payload}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {tab === 'admin' && protocol === 'KAFKA' && (
        <KafkaAdminPanel connectionId={cid} defaultTopic={selectedTopic || session.destination} />
      )}

      {tab === 'live' && <LiveViewPanel session={session} />}
    </div>
  );
}
