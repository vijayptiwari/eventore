import { useMutation, useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { api, InspectNotSupportedError } from '../api/client';
import { useControlPlane } from '../hooks/useControlPlane';
import type { LiveStreamSession } from '../stream/types';
import type { ProtocolType } from '../api/types';
import KafkaAdminPanel from './KafkaAdminPanel';
import LiveViewPanel from './LiveViewPanel';
import StreamInspectorGroupsTab from './StreamInspectorGroupsTab';
import StreamInspectorLagTab from './StreamInspectorLagTab';
import StreamInspectorOverviewTab from './StreamInspectorOverviewTab';
import StreamInspectorSearchTab from './StreamInspectorSearchTab';
import StreamInspectorShardsTab from './StreamInspectorShardsTab';
import StreamInspectorTopicsTab from './StreamInspectorTopicsTab';
import { hasInspectFeature } from '../utils/inspectFeatures';

type Tab = 'overview' | 'topics' | 'groups' | 'lag' | 'search' | 'shards' | 'admin' | 'live';

function groupsTabLabel(protocol: ProtocolType): string {
  if (protocol === 'RABBITMQ') return 'Queues';
  if (protocol === 'PULSAR') return 'Subscriptions';
  return 'Consumer groups';
}

function lagTabLabel(protocol: ProtocolType): string {
  if (protocol === 'RABBITMQ') return 'Queue depth';
  if (protocol === 'PULSAR') return 'Backlog';
  return 'Lag';
}

interface Props {
  session: LiveStreamSession;
}

export default function StreamInspector({ session }: Props) {
  const [tab, setTab] = useState<Tab>('overview');
  const [topicFilter, setTopicFilter] = useState('');
  // Per-tab topic state so edits on one tab don't leak into the others.
  const [detailsTopic, setDetailsTopic] = useState(session.destination);
  const [lagTopic, setLagTopic] = useState(session.destination);
  const [searchTopic, setSearchTopic] = useState(session.destination);
  const [selectedGroup, setSelectedGroup] = useState('');
  const [searchPayload, setSearchPayload] = useState('');
  const [searchKey, setSearchKey] = useState('');
  const [dumpStartAt, setDumpStartAt] = useState<'latest' | 'earliest'>('latest');
  const cid = session.connectionId;
  const protocol = session.protocol;
  const { adminProtocols } = useControlPlane();
  const streamName = session.destination;

  useEffect(() => {
    setDetailsTopic(session.destination);
    setLagTopic(session.destination);
    setSearchTopic(session.destination);
  }, [session.destination]);

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
    queryKey: ['inspect-topic', cid, detailsTopic],
    queryFn: () => api.inspectTopic(cid, detailsTopic),
    enabled: !!detailsTopic && tab === 'topics',
  });

  const feats = capabilities?.features;
  const canSearch = hasInspectFeature(feats, 'message-search');
  const canLag = hasInspectFeature(feats, 'lag') || hasInspectFeature(feats, 'backlog');
  const canGroups =
    hasInspectFeature(feats, 'subscriptions') ||
    hasInspectFeature(feats, 'queues') ||
    hasInspectFeature(feats, 'consumer-groups');
  const canShards = protocol === 'KINESIS' && adminProtocols.includes('KINESIS');
  const canDump = canSearch;

  const { data: lag, refetch: refetchLag } = useQuery({
    queryKey: ['inspect-lag', cid, selectedGroup, lagTopic],
    queryFn: () => api.inspectLag(cid, selectedGroup, lagTopic || undefined),
    enabled: tab === 'lag' && !!selectedGroup && canLag,
  });

  const searchMutation = useMutation({
    mutationFn: () =>
      api.inspectSearch(cid, {
        topic: searchTopic,
        payloadContains: searchPayload || undefined,
        keyContains: searchKey || undefined,
        maxMessages: 50,
        startAt: 'latest',
      }),
  });

  const dumpMutation = useMutation({
    mutationFn: () =>
      api.inspectSearch(cid, {
        topic: detailsTopic,
        maxMessages: 200,
        startAt: dumpStartAt,
      }),
  });

  const {
    data: shards,
    isLoading: shardsLoading,
    error: shardsError,
  } = useQuery({
    queryKey: ['kinesis-shards', cid, streamName],
    queryFn: () => api.kinesisListShards(cid, streamName),
    enabled: tab === 'shards' && canShards && !!streamName,
  });

  const tabs: { id: Tab; label: string; show: boolean }[] = [
    { id: 'overview', label: 'Overview', show: true },
    { id: 'topics', label: protocol === 'RABBITMQ' ? 'Queues' : 'Topics / Queues', show: true },
    {
      id: 'groups',
      label: groupsTabLabel(protocol),
      show: canGroups,
    },
    { id: 'lag', label: lagTabLabel(protocol), show: canLag },
    { id: 'search', label: 'Message search', show: canSearch },
    { id: 'shards', label: 'Shards', show: canShards },
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
        <StreamInspectorOverviewTab
          clusterLoading={clusterLoading}
          clusterError={clusterError}
          capabilities={capabilities}
          cluster={cluster}
          brokers={brokers}
        />
      )}

      {tab === 'topics' && (
        <StreamInspectorTopicsTab
          connectionId={cid}
          connectionName={session.connectionName}
          protocol={protocol}
          topicFilter={topicFilter}
          onTopicFilterChange={setTopicFilter}
          topics={topics}
          detailsTopic={detailsTopic}
          onDetailsTopicChange={setDetailsTopic}
          topicDetail={topicDetail}
          canDump={canDump}
          dumpStartAt={dumpStartAt}
          onDumpStartAtChange={setDumpStartAt}
          dumpMutation={dumpMutation}
        />
      )}

      {tab === 'groups' && (
        <StreamInspectorGroupsTab
          protocol={protocol}
          groups={groups}
          selectedGroup={selectedGroup}
          onSelectedGroupChange={setSelectedGroup}
          groupDetail={groupDetail}
        />
      )}

      {tab === 'lag' && canLag && (
        <StreamInspectorLagTab
          groups={groups}
          selectedGroup={selectedGroup}
          onSelectedGroupChange={setSelectedGroup}
          lagTopic={lagTopic}
          onLagTopicChange={setLagTopic}
          onRefreshLag={() => refetchLag()}
          lag={lag}
        />
      )}

      {tab === 'search' && canSearch && (
        <>
          {searchMutation.error instanceof InspectNotSupportedError && (
            <p className="stream-error">
              Not supported for {protocol}: {searchMutation.error.message}
            </p>
          )}
          <StreamInspectorSearchTab
            connectionId={cid}
            searchTopic={searchTopic}
            onSearchTopicChange={setSearchTopic}
            searchPayload={searchPayload}
            onSearchPayloadChange={setSearchPayload}
            searchKey={searchKey}
            onSearchKeyChange={setSearchKey}
            searchMutation={searchMutation}
          />
        </>
      )}

      {tab === 'shards' && canShards && (
        <StreamInspectorShardsTab
          streamName={streamName}
          shards={shards}
          isLoading={shardsLoading}
          error={shardsError}
        />
      )}

      {tab === 'admin' && protocol === 'KAFKA' && (
        <KafkaAdminPanel connectionId={cid} defaultTopic={detailsTopic || session.destination} />
      )}

      {tab === 'live' && <LiveViewPanel session={session} />}
    </div>
  );
}
