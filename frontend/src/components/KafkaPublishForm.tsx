import { useMutation } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../api/client';

function parseHeaderLines(text: string): Record<string, string> {
  const headers: Record<string, string> = {};
  for (const line of text.split('\n')) {
    const trimmed = line.trim();
    if (!trimmed || !trimmed.includes('=')) continue;
    const idx = trimmed.indexOf('=');
    headers[trimmed.slice(0, idx).trim()] = trimmed.slice(idx + 1).trim();
  }
  return headers;
}

interface Props {
  connectionId: string;
  topic: string;
  onTopicChange: (topic: string) => void;
}

export default function KafkaPublishForm({ connectionId, topic, onTopicChange }: Props) {
  const [publishKey, setPublishKey] = useState('');
  const [publishPartition, setPublishPartition] = useState('');
  const [publishPayload, setPublishPayload] = useState('');
  const [publishHeaders, setPublishHeaders] = useState('correlationId=evt-1\ncontent-type=application/json');
  const [flushProducer, setFlushProducer] = useState(true);

  const publishMutation = useMutation({
    mutationFn: () => {
      const headers = parseHeaderLines(publishHeaders);
      if (publishKey) headers.key = publishKey;
      if (publishPartition) headers.partition = publishPartition;
      return api.kafkaPublish(connectionId, {
        destination: topic,
        payload: publishPayload,
        headers,
        flush: flushProducer,
      });
    },
  });

  return (
    <div className="card">
      <h3>Publish message</h3>
      <div className="form-grid">
        <div className="form-row">
          <label>Topic</label>
          <input value={topic} onChange={(e) => onTopicChange(e.target.value)} />
        </div>
        <div className="form-row">
          <label>Message key</label>
          <input value={publishKey} onChange={(e) => setPublishKey(e.target.value)} placeholder="optional" />
        </div>
        <div className="form-row">
          <label>Partition</label>
          <input
            value={publishPartition}
            onChange={(e) => setPublishPartition(e.target.value)}
            placeholder="optional"
          />
        </div>
      </div>
      <div className="form-row">
        <label>Payload</label>
        <textarea rows={4} value={publishPayload} onChange={(e) => setPublishPayload(e.target.value)} />
      </div>
      <div className="form-row">
        <label>Record headers (key=value per line)</label>
        <textarea rows={3} value={publishHeaders} onChange={(e) => setPublishHeaders(e.target.value)} />
      </div>
      <label className="inspector-meta">
        <input
          type="checkbox"
          checked={flushProducer}
          onChange={(e) => setFlushProducer(e.target.checked)}
        />{' '}
        Flush producer after send
      </label>
      <button
        type="button"
        disabled={publishMutation.isPending || !topic}
        onClick={() => publishMutation.mutate()}
      >
        Publish
      </button>
      {publishMutation.isError && (
        <p className="stream-error">{String(publishMutation.error)}</p>
      )}
      {publishMutation.data && (
        <pre className="inspector-pre">{JSON.stringify(publishMutation.data, null, 2)}</pre>
      )}
    </div>
  );
}
