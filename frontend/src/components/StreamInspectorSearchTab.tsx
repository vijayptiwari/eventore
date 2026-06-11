import type { UseMutationResult } from '@tanstack/react-query';
import type { UnifiedMessage } from '../api/types';
import ExportResultActions from './ExportResultActions';

interface Props {
  connectionId: string;
  searchTopic: string;
  onSearchTopicChange: (value: string) => void;
  searchPayload: string;
  onSearchPayloadChange: (value: string) => void;
  searchKey: string;
  onSearchKeyChange: (value: string) => void;
  searchMutation: UseMutationResult<UnifiedMessage[], Error, void, unknown>;
}

export default function StreamInspectorSearchTab({
  connectionId,
  searchTopic,
  onSearchTopicChange,
  searchPayload,
  onSearchPayloadChange,
  searchKey,
  onSearchKeyChange,
  searchMutation,
}: Props) {
  return (
    <div className="card">
      <div className="form-grid">
        <div className="form-row">
          <label>Topic</label>
          <input value={searchTopic} onChange={(e) => onSearchTopicChange(e.target.value)} />
        </div>
        <div className="form-row">
          <label>Payload contains</label>
          <input value={searchPayload} onChange={(e) => onSearchPayloadChange(e.target.value)} />
        </div>
        <div className="form-row">
          <label>Key contains</label>
          <input value={searchKey} onChange={(e) => onSearchKeyChange(e.target.value)} />
        </div>
      </div>
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
  );
}
