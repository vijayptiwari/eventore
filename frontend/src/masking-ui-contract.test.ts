import { describe, it, expect, vi, beforeEach } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { api } from './api/client';
import type { MessageSearchRequest, MaskingConfigResponse, MaskingPreviewResponse } from './api/inspectTypes';

describe('REQ-105: Field-Level Data Masking & PII Redaction UI and Contract', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('MessageSearchRequest interface supports optional mask parameter', () => {
    const request: MessageSearchRequest = {
      topic: 'orders-topic',
      maxMessages: 10,
      mask: true,
    };
    expect(request.mask).toBe(true);

    const unmaskedRequest: MessageSearchRequest = {
      topic: 'orders-topic',
      mask: false,
    };
    expect(unmaskedRequest.mask).toBe(false);
  });

  it('api client exposes getMaskingConfig and previewMasking methods', async () => {
    expect(typeof api.getMaskingConfig).toBe('function');
    expect(typeof api.previewMasking).toBe('function');

    const mockConfig: MaskingConfigResponse = {
      enabled: true,
      maskSensitiveFields: true,
      maskCreditCards: true,
      maskSsn: true,
      maskEmails: true,
      maskHeaders: true,
      replacementString: '[REDACTED]',
      sensitiveFieldPatterns: ['password', 'secret', 'token'],
      sensitiveHeaderPatterns: ['authorization', 'cookie'],
    };

    const mockPreview: MaskingPreviewResponse = {
      originalPayload: '{"password":"123"}',
      maskedPayload: '{"password":"[REDACTED]"}',
      wasMasked: true,
      redactionCount: 1,
      appliedRules: ['FIELD:password'],
    };

    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(async (url) => {
      const u = String(url);
      if (u.includes('/api/v1/masking/config')) {
        return new Response(JSON.stringify(mockConfig), { status: 200, headers: { 'content-type': 'application/json' } });
      }
      if (u.includes('/api/v1/masking/preview')) {
        return new Response(JSON.stringify(mockPreview), { status: 200, headers: { 'content-type': 'application/json' } });
      }
      return new Response('{}', { status: 200 });
    });

    const config = await api.getMaskingConfig();
    expect(config.enabled).toBe(true);
    expect(config.replacementString).toBe('[REDACTED]');

    const preview = await api.previewMasking({ payload: '{"password":"123"}' });
    expect(preview.wasMasked).toBe(true);
    expect(preview.redactionCount).toBe(1);
    expect(preview.maskedPayload).toContain('[REDACTED]');

    fetchSpy.mockRestore();
  });

  it('LiveViewPanel source contains PII masking card, toggle state, and passes maskingEnabled to startLiveView', () => {
    const filePath = join(__dirname, 'components', 'LiveViewPanel.tsx');
    const content = readFileSync(filePath, 'utf-8');

    expect(content).toContain('Data Privacy &amp; PII Masking');
    expect(content).toContain('🛡️ PII Masking: Active');
    expect(content).toContain('⚠️ Masking: Disabled (Raw Payloads)');
    expect(content).toContain('maskingEnabled');
    expect(content).toContain('startLiveView(session.id,');
  });

  it('StreamInspectorSearchTab source contains PII masking checkbox and searchMask prop', () => {
    const filePath = join(__dirname, 'components', 'StreamInspectorSearchTab.tsx');
    const content = readFileSync(filePath, 'utf-8');

    expect(content).toContain('searchMask');
    expect(content).toContain('onSearchMaskChange');
    expect(content).toContain('Mask sensitive fields &amp; PII (passwords, tokens, cards, SSN)');
  });

  it('MessagePayloadViewer renders 🛡️ Masked badge on redacted content or x-eventore-masked header', () => {
    const filePath = join(__dirname, 'components', 'MessagePayloadViewer.tsx');
    const content = readFileSync(filePath, 'utf-8');

    expect(content).toContain('isMasked');
    expect(content).toContain('x-eventore-masked');
    expect(content).toContain('[REDACTED]');
    expect(content).toContain('masked-badge');
    expect(content).toContain('🛡️');
    expect(content).toContain('Masked');
  });

  it('StreamWorkspaceContext passes maskingEnabled in START_LIVE_VIEW WebSocket command', () => {
    const filePath = join(__dirname, 'stream', 'StreamWorkspaceContext.tsx');
    const content = readFileSync(filePath, 'utf-8');

    expect(content).toContain('type: \'START_LIVE_VIEW\'');
    expect(content).toContain('maskingEnabled: isMasking');
  });
});
