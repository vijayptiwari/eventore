import { useState } from 'react';

interface Props {
  payload: string;
  headers?: Record<string, string>;
  contentType?: string;
}

export default function MessagePayloadViewer({ payload, headers, contentType }: Props) {
  const [showRaw, setShowRaw] = useState(false);
  const [copied, setCopied] = useState(false);
  const [traceCopied, setTraceCopied] = useState(false);

  const schemaId = headers?.['x-eventore-schema-id'];
  const schemaType = headers?.['x-eventore-schema-type'] || 'AVRO';
  const isMasked =
    headers?.['x-eventore-masked'] === 'true' ||
    (payload &&
      (payload.includes('[REDACTED]') ||
        payload.includes('[CARD_REDACTED]') ||
        payload.includes('[SSN_REDACTED]') ||
        payload.includes('[EMAIL_REDACTED]')));

  const traceId =
    headers?.['x-eventore-trace-id'] ||
    headers?.['traceparent']?.split('-')[1] ||
    headers?.['b3']?.split('-')[0] ||
    headers?.['X-B3-TraceId'] ||
    headers?.['x-b3-traceid'] ||
    headers?.['uber-trace-id']?.split(':')[0] ||
    (headers?.['X-Amzn-Trace-Id']?.includes('Root=') ? headers['X-Amzn-Trace-Id'].split('Root=')[1]?.split(';')[0] : undefined);

  const spanId =
    headers?.['x-eventore-span-id'] ||
    headers?.['traceparent']?.split('-')[2] ||
    headers?.['b3']?.split('-')[1] ||
    headers?.['X-B3-SpanId'] ||
    headers?.['x-b3-spanid'];

  const traceFormat =
    headers?.['x-eventore-trace-format'] ||
    (headers?.['traceparent'] ? 'W3C' : headers?.['b3'] || headers?.['X-B3-TraceId'] ? 'B3' : headers?.['X-Amzn-Trace-Id'] ? 'X-Ray' : undefined);

  const isJson =
    contentType?.includes('json') ||
    (payload &&
      ((payload.trim().startsWith('{') && payload.trim().endsWith('}')) ||
        (payload.trim().startsWith('[') && payload.trim().endsWith(']'))));

  let formatted = payload;
  if (!showRaw && isJson) {
    try {
      const parsed = JSON.parse(payload.trim());
      formatted = JSON.stringify(parsed, null, 2);
    } catch {
      // keep raw
    }
  }

  const handleCopy = () => {
    void navigator.clipboard.writeText(payload);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="message-payload-viewer">
      {(schemaId || isJson || isMasked || traceId) && (
        <div className="payload-meta-bar">
          {schemaId && (
            <span
              className="schema-badge"
              title={`Resolved via Confluent Schema Registry (Schema ID: ${schemaId})`}
            >
              <span className="schema-badge-icon">⚡</span>
              Schema #{schemaId} · {schemaType}
            </span>
          )}
          {isMasked && (
            <span
              className="schema-badge masked-badge"
              style={{
                background: 'rgba(34, 197, 94, 0.15)',
                color: '#22c55e',
                borderColor: 'rgba(34, 197, 94, 0.3)',
              }}
              title="Sensitive fields or PII data redacted by EventOre DataMasker"
            >
              <span className="schema-badge-icon">🛡️</span>
              Masked
            </span>
          )}
          {traceId && (
            <span
              className="schema-badge trace-badge"
              style={{
                background: 'rgba(99, 102, 241, 0.15)',
                color: '#818cf8',
                borderColor: 'rgba(99, 102, 241, 0.3)',
                cursor: 'pointer',
              }}
              title={`Distributed Trace (${traceFormat ?? 'Trace'}): ${traceId}${spanId ? ` · Span: ${spanId}` : ''}\nClick to copy Trace ID or open in APM`}
              onClick={() => {
                void navigator.clipboard.writeText(traceId);
                setTraceCopied(true);
                setTimeout(() => setTraceCopied(false), 2000);
              }}
            >
              <span className="schema-badge-icon">🔍</span>
              Trace: {traceId.length > 8 ? traceId.slice(0, 8) : traceId}
              {traceCopied && <span style={{ marginLeft: 4, fontSize: '0.7rem' }}>✓</span>}
              <a
                href={`http://localhost:16686/trace/${traceId}`}
                target="_blank"
                rel="noreferrer"
                style={{ marginLeft: 4, color: 'inherit', textDecoration: 'none' }}
                title="Open in Jaeger / APM"
                onClick={(e) => e.stopPropagation()}
              >
                ↗
              </a>
            </span>
          )}
          <div className="payload-controls">
            {isJson && (
              <button
                type="button"
                className="payload-toggle-btn"
                onClick={() => setShowRaw(!showRaw)}
                title="Toggle JSON indentation"
              >
                {showRaw ? 'Pretty' : 'Raw'}
              </button>
            )}
            <button
              type="button"
              className="payload-copy-btn"
              onClick={handleCopy}
              title="Copy payload"
            >
              {copied ? 'Copied!' : 'Copy'}
            </button>
          </div>
        </div>
      )}
      <pre className="message-payload-content">{formatted}</pre>
    </div>
  );
}
