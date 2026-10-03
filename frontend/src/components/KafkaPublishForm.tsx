import { useMutation } from '@tanstack/react-query';
import { useState } from 'react';
import { api, type SchemaValidationResult } from '../api/client';

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

  // Schema-aware state
  const [schemaId, setSchemaId] = useState('');
  const [loadingTemplate, setLoadingTemplate] = useState(false);
  const [validating, setValidating] = useState(false);
  const [validationResult, setValidationResult] = useState<SchemaValidationResult | null>(null);
  const [schemaActionError, setSchemaActionError] = useState<string | null>(null);

  const handleLoadTemplate = async () => {
    const idNum = parseInt(schemaId.trim(), 10);
    if (isNaN(idNum) || idNum <= 0) {
      setSchemaActionError('Please enter a valid numeric Schema ID');
      return;
    }
    setSchemaActionError(null);
    setLoadingTemplate(true);
    try {
      const res = await api.getSchemaTemplate(idNum);
      if (res?.template) {
        setPublishPayload(res.template);
        setValidationResult({ valid: true, errors: [] });
      }
    } catch (err: unknown) {
      setSchemaActionError(err instanceof Error ? err.message : String(err));
    } finally {
      setLoadingTemplate(false);
    }
  };

  const handleValidateSchema = async () => {
    const idNum = parseInt(schemaId.trim(), 10);
    if (isNaN(idNum) || idNum <= 0) {
      setSchemaActionError('Please enter a valid numeric Schema ID to validate against');
      return;
    }
    setSchemaActionError(null);
    setValidating(true);
    try {
      const res = await api.validateSchemaPayload(idNum, publishPayload);
      setValidationResult(res);
    } catch (err: unknown) {
      setSchemaActionError(err instanceof Error ? err.message : String(err));
    } finally {
      setValidating(false);
    }
  };

  const publishMutation = useMutation({
    mutationFn: () => {
      const headers = parseHeaderLines(publishHeaders);
      if (publishKey) headers.key = publishKey;
      if (publishPartition) headers.partition = publishPartition;
      if (schemaId.trim()) {
        headers['x-eventore-schema-id'] = schemaId.trim();
      }
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

      <div className="form-row" style={{ marginTop: 8, padding: '10px 12px', background: 'var(--bg-card-hover, rgba(255,255,255,0.02))', borderRadius: 6, border: '1px solid var(--border-color, #333)' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 8 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <label style={{ margin: 0, fontWeight: 600 }}>Schema ID:</label>
            <input
              type="number"
              style={{ width: 110 }}
              placeholder="e.g. 1042"
              value={schemaId}
              onChange={(e) => {
                setSchemaId(e.target.value);
                setValidationResult(null);
                setSchemaActionError(null);
              }}
            />
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            <button
              type="button"
              className="btn btn-secondary"
              disabled={loadingTemplate || !schemaId.trim()}
              onClick={handleLoadTemplate}
              title="Fetch schema from registry and fill payload with default JSON template"
            >
              {loadingTemplate ? 'Loading…' : 'Load Template'}
            </button>
            <button
              type="button"
              className="btn btn-secondary"
              disabled={validating || !schemaId.trim() || !publishPayload.trim()}
              onClick={handleValidateSchema}
              title="Pre-flight validate current payload against schema"
            >
              {validating ? 'Validating…' : 'Validate Schema'}
            </button>
          </div>
        </div>

        {validationResult && (
          <div style={{ marginTop: 8 }}>
            {validationResult.valid ? (
              <span className="tag tag-ok">✓ Payload matches Schema #{schemaId.trim()}</span>
            ) : (
              <div className="stream-error" style={{ marginTop: 4 }}>
                ✗ Schema validation failed:{' '}
                {validationResult.errors?.length ? validationResult.errors.join('; ') : 'Invalid payload format'}
              </div>
            )}
          </div>
        )}

        {schemaActionError && (
          <div className="stream-error" style={{ marginTop: 6 }}>
            {schemaActionError}
          </div>
        )}
      </div>

      <div className="form-row">
        <label>Payload</label>
        <textarea
          rows={5}
          value={publishPayload}
          onChange={(e) => {
            setPublishPayload(e.target.value);
            if (validationResult) setValidationResult(null);
          }}
          placeholder="JSON or raw payload string"
        />
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

