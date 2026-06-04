import type { UnifiedMessage } from '../api/types';
import { exportMessages, exportPayload, type ExportFormat } from '../utils/exportData';

interface Props {
  /** Base name without extension (timestamp appended automatically). */
  filenameBase: string;
  messages?: UnifiedMessage[];
  /** Arbitrary JSON payload (topic list, topic detail, lag, etc.). */
  jsonData?: unknown;
  meta?: Record<string, unknown>;
  disabled?: boolean;
  className?: string;
}

export default function ExportResultActions({
  filenameBase,
  messages,
  jsonData,
  meta,
  disabled,
  className,
}: Props) {
  const hasMessages = (messages?.length ?? 0) > 0;
  const hasJson = jsonData !== undefined && jsonData !== null;

  if (!hasMessages && !hasJson) {
    return null;
  }

  const exportMsg = (format: ExportFormat) => {
    if (!messages?.length) return;
    exportMessages(filenameBase, messages, format, meta);
  };

  return (
    <div className={`export-actions ${className ?? ''}`.trim()}>
      <span className="export-actions-label">Export</span>
      {hasMessages && (
        <>
          <button type="button" className="secondary" disabled={disabled} onClick={() => exportMsg('json')}>
            Messages JSON
          </button>
          <button type="button" className="secondary" disabled={disabled} onClick={() => exportMsg('csv')}>
            Messages CSV
          </button>
          <button type="button" className="secondary" disabled={disabled} onClick={() => exportMsg('ndjson')}>
            NDJSON
          </button>
        </>
      )}
      {hasJson && (
        <button
          type="button"
          className="secondary"
          disabled={disabled}
          onClick={() => exportPayload(filenameBase, jsonData, meta)}
        >
          {hasMessages ? 'Data JSON' : 'JSON'}
        </button>
      )}
    </div>
  );
}
