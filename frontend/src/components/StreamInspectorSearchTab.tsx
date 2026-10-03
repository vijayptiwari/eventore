import type { UseMutationResult } from '@tanstack/react-query';
import type { UnifiedMessage } from '../api/types';
import ExportResultActions from './ExportResultActions';
import MessagePayloadViewer from './MessagePayloadViewer';

interface Props {
  connectionId: string;
  protocol?: string;
  searchTopic: string;
  onSearchTopicChange: (value: string) => void;
  searchPartition: string;
  onSearchPartitionChange: (value: string) => void;
  searchPayload: string;
  onSearchPayloadChange: (value: string) => void;
  searchKey: string;
  onSearchKeyChange: (value: string) => void;
  searchTimestamp?: string;
  onSearchTimestampChange?: (value: string) => void;
  searchMutation: UseMutationResult<UnifiedMessage[], Error, void, unknown>;
}

export default function StreamInspectorSearchTab({
  connectionId,
  protocol,
  searchTopic,
  onSearchTopicChange,
  searchPartition,
  onSearchPartitionChange,
  searchPayload,
  onSearchPayloadChange,
  searchKey,
  onSearchKeyChange,
  searchTimestamp = '',
  onSearchTimestampChange,
  searchMutation,
}: Props) {
  return (
    <div className="card">
      <div className="form-grid">
        <div className="form-row">
          <label>Topic / Queue</label>
          <input value={searchTopic} onChange={(e) => onSearchTopicChange(e.target.value)} placeholder="Destination name" />
        </div>
        <div className="form-row">
          <label>{protocol === 'AZURE_SERVICE_BUS' ? 'Subscription (for topics)' : 'Partition (optional)'}</label>
          <input
            value={searchPartition}
            onChange={(e) => onSearchPartitionChange(e.target.value)}
            placeholder={protocol === 'AZURE_SERVICE_BUS' ? 'Subscription name (leave blank for queues)' : 'e.g. 0'}
          />
        </div>
        <div className="form-row">
          <label>Payload contains</label>
          <input value={searchPayload} onChange={(e) => onSearchPayloadChange(e.target.value)} />
        </div>
        <div className="form-row">
          <label>Key contains</label>
          <input value={searchKey} onChange={(e) => onSearchKeyChange(e.target.value)} />
        </div>
        <div className="form-row">
          <label>From Timestamp (Time-Travel)</label>
          <input
            value={searchTimestamp}
            onChange={(e) => onSearchTimestampChange?.(e.target.value)}
            placeholder="e.g. 2026-10-03T18:00:00Z"
          />
        </div>
      </div>
      {protocol === 'AZURE_SERVICE_BUS' && (
        <p className="inspector-meta">
          Azure Service Bus uses non-destructive message peek. For topics, specify the subscription name in the Subscription field.
        </p>
      )}
      <button
        type="button"
        disabled={searchMutation.isPending || !searchTopic}
        onClick={() => searchMutation.mutate()}
      >
        Search (sample)
      </button>
      {searchMutation.isError && (
        <p className="stream-error">{String(searchMutation.error)}</p>
      )}
      <ExportResultActions
        filenameBase={`message-search_${searchTopic}`}
        messages={searchMutation.data}
        meta={{
          connectionId,
          topic: searchTopic,
          partition: searchPartition || undefined,
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
              <td className="message-payload">
                <MessagePayloadViewer payload={m.payload} headers={m.headers} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
