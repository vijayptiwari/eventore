import type { UseMutationResult } from '@tanstack/react-query';
import type { ProtocolType, UnifiedMessage } from '../api/types';
import ExportResultActions from './ExportResultActions';

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
        <>
          <ExportResultActions
            filenameBase={`topic-detail_${detailsTopic}`}
            jsonData={topicDetail}
            meta={{ connectionId, protocol }}
          />
          <pre className="inspector-pre">{JSON.stringify(topicDetail, null, 2)}</pre>
        </>
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
                    <td className="message-payload">{m.payload}</td>
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
