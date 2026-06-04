import type { UnifiedMessage } from '../api/types';

export type ExportFormat = 'json' | 'csv' | 'ndjson';

export function sanitizeExportFilename(name: string): string {
  const cleaned = name
    .replace(/[^\w.\-]+/g, '_')
    .replace(/_+/g, '_')
    .replace(/^_|_$/g, '');
  return cleaned || 'export';
}

function downloadBlob(filename: string, content: string, mimeType: string): void {
  const blob = new Blob([content], { type: mimeType });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}

function csvEscape(value: string): string {
  if (/[",\n\r]/.test(value)) {
    return `"${value.replace(/"/g, '""')}"`;
  }
  return value;
}

export function exportJsonFile(filename: string, data: unknown): void {
  const body = JSON.stringify(data, null, 2);
  downloadBlob(filename, body, 'application/json;charset=utf-8');
}

export function exportMessages(
  filenameBase: string,
  messages: UnifiedMessage[],
  format: ExportFormat,
  meta?: Record<string, unknown>,
): void {
  const base = sanitizeExportFilename(filenameBase);
  const stamp = new Date().toISOString().replace(/[:.]/g, '-');

  if (format === 'ndjson') {
    const lines = messages.map((m) => JSON.stringify(m)).join('\n');
    downloadBlob(`${base}_${stamp}.ndjson`, lines, 'application/x-ndjson;charset=utf-8');
    return;
  }

  if (format === 'csv') {
    const header = [
      'timestamp',
      'destination',
      'protocol',
      'direction',
      'partition',
      'offset',
      'key',
      'contentType',
      'headers',
      'payload',
    ];
    const rows = messages.map((m) => {
      const partition = m.headers?.partition ?? '';
      const offset = m.headers?.offset ?? '';
      const key = m.headers?.key ?? '';
      const headerJson = JSON.stringify(m.headers ?? {});
      return [
        m.timestamp,
        m.destination,
        m.protocol,
        m.direction,
        partition,
        offset,
        key,
        m.contentType,
        headerJson,
        m.payload,
      ]
        .map((c) => csvEscape(String(c ?? '')))
        .join(',');
    });
    const csv = [header.join(','), ...rows].join('\n');
    downloadBlob(`${base}_${stamp}.csv`, csv, 'text/csv;charset=utf-8');
    return;
  }

  exportJsonFile(`${base}_${stamp}.json`, {
    exportedAt: new Date().toISOString(),
    count: messages.length,
    meta: meta ?? {},
    messages,
  });
}

export function exportPayload(
  filenameBase: string,
  payload: unknown,
  meta?: Record<string, unknown>,
): void {
  const base = sanitizeExportFilename(filenameBase);
  const stamp = new Date().toISOString().replace(/[:.]/g, '-');
  exportJsonFile(`${base}_${stamp}.json`, {
    exportedAt: new Date().toISOString(),
    meta: meta ?? {},
    data: payload,
  });
}
