import { useState } from 'react';

interface Props {
  payload: string;
  headers?: Record<string, string>;
  contentType?: string;
}

export default function MessagePayloadViewer({ payload, headers, contentType }: Props) {
  const [showRaw, setShowRaw] = useState(false);
  const [copied, setCopied] = useState(false);

  const schemaId = headers?.['x-eventore-schema-id'];
  const schemaType = headers?.['x-eventore-schema-type'] || 'AVRO';
  const isMasked =
    headers?.['x-eventore-masked'] === 'true' ||
    (payload &&
      (payload.includes('[REDACTED]') ||
        payload.includes('[CARD_REDACTED]') ||
        payload.includes('[SSN_REDACTED]') ||
        payload.includes('[EMAIL_REDACTED]')));

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
      {(schemaId || isJson || isMasked) && (
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
              title="Sensitive fields or PII data redacted by Eventore DataMasker"
            >
              <span className="schema-badge-icon">🛡️</span>
              Masked
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
