import { describe, it, expect, vi, beforeEach } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { api } from './api/client';
import type { TraceContext, TracingConfigResponse } from './api/inspectTypes';

describe('REQ-111: OpenTelemetry Distributed Tracing & W3C TraceContext UI and Contract', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('TraceContext and TracingConfigResponse interfaces are valid', () => {
    const traceCtx: TraceContext = {
      traceId: '4bf92f3577b34da6a3ce929d0e0e4736',
      spanId: '00f067aa0ba902b7',
      sampled: true,
      format: 'W3C',
      rawHeader: '00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01',
    };
    expect(traceCtx.traceId).toBe('4bf92f3577b34da6a3ce929d0e0e4736');
    expect(traceCtx.format).toBe('W3C');

    const config: TracingConfigResponse = {
      enabled: true,
      viewerType: 'JAEGER',
      urlTemplate: 'http://localhost:16686/trace/{traceId}',
      injectOnPublish: true,
    };
    expect(config.enabled).toBe(true);
    expect(config.viewerType).toBe('JAEGER');
  });

  it('api client exposes getTracingConfig, extractTraceContext, and resolveTraceUrl methods', async () => {
    expect(typeof api.getTracingConfig).toBe('function');
    expect(typeof api.extractTraceContext).toBe('function');
    expect(typeof api.resolveTraceUrl).toBe('function');

    const mockConfig: TracingConfigResponse = {
      enabled: true,
      viewerType: 'JAEGER',
      urlTemplate: 'http://localhost:16686/trace/{traceId}',
      injectOnPublish: true,
    };

    const mockExtracted: TraceContext = {
      traceId: '4bf92f3577b34da6a3ce929d0e0e4736',
      spanId: '00f067aa0ba902b7',
      sampled: true,
      format: 'W3C',
    };

    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(async (url) => {
      const u = String(url);
      if (u.includes('/api/v1/tracing/config')) {
        return new Response(JSON.stringify(mockConfig), { status: 200, headers: { 'content-type': 'application/json' } });
      }
      if (u.includes('/api/v1/tracing/extract')) {
        return new Response(JSON.stringify(mockExtracted), { status: 200, headers: { 'content-type': 'application/json' } });
      }
      if (u.includes('/api/v1/tracing/url')) {
        return new Response(JSON.stringify({ traceId: '123', url: 'http://localhost:16686/trace/123' }), { status: 200, headers: { 'content-type': 'application/json' } });
      }
      return new Response('{}', { status: 200 });
    });

    const cfg = await api.getTracingConfig();
    expect(cfg.viewerType).toBe('JAEGER');

    const extracted = await api.extractTraceContext({ traceparent: '00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01' });
    expect(extracted?.traceId).toBe('4bf92f3577b34da6a3ce929d0e0e4736');

    const urlRes = await api.resolveTraceUrl('123');
    expect(urlRes.url).toBe('http://localhost:16686/trace/123');

    fetchSpy.mockRestore();
  });

  it('MessagePayloadViewer source contains trace badge, external APM link, and copy action', () => {
    const filePath = join(__dirname, 'components', 'MessagePayloadViewer.tsx');
    const content = readFileSync(filePath, 'utf-8');

    expect(content).toContain('trace-badge');
    expect(content).toContain('Trace:');
    expect(content).toContain('traceId');
    expect(content).toContain('spanId');
    expect(content).toContain('traceFormat');
    expect(content).toContain('http://localhost:16686/trace/');
    expect(content).toContain('Open in Jaeger / APM');
  });

  it('KafkaPublishForm source contains W3C Trace Context injection checkbox', () => {
    const filePath = join(__dirname, 'components', 'KafkaPublishForm.tsx');
    const content = readFileSync(filePath, 'utf-8');

    expect(content).toContain('injectTrace');
    expect(content).toContain('Inject W3C Trace Context (traceparent)');
  });
});
