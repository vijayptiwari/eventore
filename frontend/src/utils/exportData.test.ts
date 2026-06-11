import { afterEach, describe, expect, it, vi, type MockInstance } from 'vitest';
import { csvEscape, exportMessages, sanitizeExportFilename } from './exportData';
import type { UnifiedMessage } from '../api/types';

describe('exportData', () => {
  it('sanitizes unsafe filename characters', () => {
    expect(sanitizeExportFilename('orders/live view')).toBe('orders_live_view');
    expect(sanitizeExportFilename('***')).toBe('export');
  });
});

describe('csvEscape', () => {
  it('passes plain values through unchanged', () => {
    expect(csvEscape('hello')).toBe('hello');
    expect(csvEscape('123')).toBe('123');
  });

  it('quotes values containing commas, quotes, or newlines', () => {
    expect(csvEscape('a,b')).toBe('"a,b"');
    expect(csvEscape('line1\nline2')).toBe('"line1\nline2"');
  });

  it('doubles embedded quotes', () => {
    expect(csvEscape('say "hi"')).toBe('"say ""hi"""');
  });

  it('neutralizes spreadsheet formula injection prefixes', () => {
    expect(csvEscape('=SUM(A1:A2)')).toBe("'=SUM(A1:A2)");
    expect(csvEscape('+1+2')).toBe("'+1+2");
    expect(csvEscape('-1-2')).toBe("'-1-2");
    expect(csvEscape('@cmd')).toBe("'@cmd");
  });

  it('quotes formula-guarded values that also contain separators', () => {
    expect(csvEscape('=1,2')).toBe('"\'=1,2"');
  });
});

describe('exportMessages assembly', () => {
  const message: UnifiedMessage = {
    id: 'm1',
    destination: 'orders',
    headers: { partition: '0', offset: '42', key: 'k1' },
    payload: '=HYPERLINK("http://evil")',
    contentType: 'application/json',
    timestamp: '2026-01-01T00:00:00Z',
    protocol: 'KAFKA',
    direction: 'IN',
    connectionId: 'conn-1',
  };

  let capturedBlobs: Blob[] = [];
  let clickSpy: MockInstance | undefined;
  const originalCreate = URL.createObjectURL;
  const originalRevoke = URL.revokeObjectURL;

  async function readBlobText(blob: Blob): Promise<string> {
    if (typeof blob.text === 'function') {
      return blob.text();
    }
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result ?? ''));
      reader.onerror = () => reject(reader.error);
      reader.readAsText(blob);
    });
  }

  function mockDownload() {
    capturedBlobs = [];
    URL.createObjectURL = ((blob: Blob) => {
      capturedBlobs.push(blob);
      return 'blob:mock';
    }) as typeof URL.createObjectURL;
    URL.revokeObjectURL = (() => {}) as typeof URL.revokeObjectURL;
    clickSpy = vi.spyOn(HTMLElement.prototype, 'click').mockImplementation(() => {});
  }

  afterEach(() => {
    URL.createObjectURL = originalCreate;
    URL.revokeObjectURL = originalRevoke;
    clickSpy?.mockRestore();
  });

  it('assembles CSV with a header row and formula-guarded payload', async () => {
    mockDownload();
    exportMessages('orders', [message], 'csv');

    expect(capturedBlobs).toHaveLength(1);
    const text = await readBlobText(capturedBlobs[0]);
    const lines = text.split('\n');
    expect(lines[0]).toBe(
      'timestamp,destination,protocol,direction,partition,offset,key,contentType,headers,payload',
    );
    expect(lines[1]).toContain('2026-01-01T00:00:00Z,orders,KAFKA,IN,0,42,k1');
    expect(lines[1]).toContain('\'=HYPERLINK');
    expect(clickSpy).toHaveBeenCalledTimes(1);
  });

  it('assembles NDJSON with one unmodified JSON document per line', async () => {
    mockDownload();
    exportMessages('orders', [message, { ...message, id: 'm2' }], 'ndjson');

    expect(capturedBlobs).toHaveLength(1);
    const text = await readBlobText(capturedBlobs[0]);
    const lines = text.split('\n');
    expect(lines).toHaveLength(2);
    expect(JSON.parse(lines[0])).toEqual(message);
    expect(JSON.parse(lines[1]).id).toBe('m2');
    // NDJSON must not apply the CSV formula guard.
    expect(JSON.parse(lines[0]).payload).toBe('=HYPERLINK("http://evil")');
  });
});
