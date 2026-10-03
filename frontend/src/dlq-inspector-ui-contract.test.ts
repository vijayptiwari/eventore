import { describe, it, expect, vi, beforeEach } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { api } from './api/client';

describe('REQ-108: Dead Letter Queue (DLQ) Inspector & Redrive UI and API Contract', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('StreamInspector DlqTab component source verifies DLQ detection and inspection', () => {
    const filePath = join(__dirname, 'components', 'StreamInspectorDlqTab.tsx');
    const content = readFileSync(filePath, 'utf-8');

    // Verifies DLQ topic detection query and pill selection
    expect(content).toContain('api.listDlqTopics');
    expect(content).toContain('Dead Letter Queue (DLQ) & Redrive');
    expect(content).toContain('Scan DLQ Topics');
    expect(content).toContain('dlq.inferredTargetTopic');
    expect(content).toContain('DLQ Source Topic');
    expect(content).toContain('Target Primary Topic');

    // Verifies dead-letter message table and headers
    expect(content).toContain('api.inspectDlqMessages');
    expect(content).toContain('Dead-letter messages on');
    expect(content).toContain('Original Target');
    expect(content).toContain('Failure Reason');

    // Verifies diagnostic error details display
    expect(content).toContain('Reason:');
    expect(content).toContain('activeMessage.exceptionClass');

    // Verifies single-message inspection/edit and redrive modal
    expect(content).toContain('Inspect & Redrive');
    expect(content).toContain('Redrive Dead-Letter Message');
    expect(content).toContain('Target Destination Queue / Topic');
    expect(content).toContain('Payload (editable before redrive)');

    // Verifies batch redrive trigger and confirmation
    expect(content).toContain('Batch Redrive All');
    expect(content).toContain('api.redriveDlq');
  });

  it('StreamInspector mounts the DLQ tab properly', () => {
    const filePath = join(__dirname, 'components', 'StreamInspector.tsx');
    const content = readFileSync(filePath, 'utf-8');

    expect(content).toContain("import StreamInspectorDlqTab from './StreamInspectorDlqTab'");
    expect(content).toContain("{ id: 'dlq', label: 'DLQ / Redrive', show: canSearch }");
    expect(content).toContain("<StreamInspectorDlqTab");
  });

  it('client API calls correct listDlqTopics endpoint', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => [
        {
          dlqTopic: 'orders.DLQ',
          inferredTargetTopic: 'orders',
          partitionCount: 3,
          detectionReason: 'Pattern matched: .*\\.dlq$',
        },
      ],
    });
    vi.stubGlobal('fetch', fetchMock);

    const res = await api.listDlqTopics('conn-1');
    expect(res).toHaveLength(1);
    expect(res[0].dlqTopic).toBe('orders.DLQ');
    expect(res[0].inferredTargetTopic).toBe('orders');

    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/connections/conn-1/dlq/topics'),
      expect.any(Object)
    );
  });

  it('client API calls correct inspectDlqMessages endpoint', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => [
        {
          message: {
            id: 'msg-1',
            payload: '{"orderId":"order-123"}',
            timestamp: 1727978400000,
            partition: 0,
            offset: 104,
            key: 'order-123',
            headers: { 'x-original-topic': 'orders' },
          },
          dlqTopic: 'orders.DLQ',
          originalTopic: 'orders',
          failureReason: 'Deserialization error',
          exceptionClass: 'org.apache.kafka.common.errors.SerializationException',
          stackTraceSnippet: 'at com.example.Consumer.onMessage',
        },
      ],
    });
    vi.stubGlobal('fetch', fetchMock);

    const res = await api.inspectDlqMessages('conn-1', 'orders.DLQ', 25);
    expect(res).toHaveLength(1);
    expect(res[0].dlqTopic).toBe('orders.DLQ');
    expect(res[0].originalTopic).toBe('orders');
    expect(res[0].failureReason).toBe('Deserialization error');

    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/connections/conn-1/dlq/messages?topic=orders.DLQ&max=25'),
      expect.any(Object)
    );
  });

  it('client API calls correct redriveDlq endpoint', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({
        successCount: 1,
        failureCount: 0,
        targetTopic: 'orders',
        errors: [],
        timestamp: '2026-10-03T18:05:00Z',
      }),
    });
    vi.stubGlobal('fetch', fetchMock);

    const req = {
      dlqTopic: 'orders.DLQ',
      targetTopic: 'orders',
      messages: [
        {
          messageId: 'msg-1',
          payload: '{"orderId":"order-123","fixed":true}',
          headers: { 'x-eventore-redriven-from': 'orders.DLQ' },
        },
      ],
    };

    const res = await api.redriveDlq('conn-1', req);
    expect(res.successCount).toBe(1);
    expect(res.targetTopic).toBe('orders');

    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/connections/conn-1/dlq/redrive'),
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify(req),
      })
    );
  });
});
