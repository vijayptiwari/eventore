import { describe, it, expect, vi, beforeEach } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { api } from './api/client';

describe('REQ-107: Schema-Aware Message Publishing UI and API Contract', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('KafkaPublishForm component source verifies schema-aware publish integration', () => {
    const filePath = join(__dirname, 'components', 'KafkaPublishForm.tsx');
    const content = readFileSync(filePath, 'utf-8');

    // Verifies Schema ID input
    expect(content).toContain('Schema ID:');
    expect(content).toContain('type="number"');

    // Verifies template loading and validation action buttons
    expect(content).toContain('Load Template');
    expect(content).toContain('api.getSchemaTemplate');
    expect(content).toContain('Validate Schema');
    expect(content).toContain('api.validateSchemaPayload');

    // Verifies outbound header injection
    expect(content).toContain("headers['x-eventore-schema-id']");

    // Verifies user feedback badge and error display
    expect(content).toContain('Payload matches Schema #');
    expect(content).toContain('Schema validation failed:');
  });

  it('client API calls correct schema template endpoint', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({
        schemaId: 1042,
        schemaType: 'AVRO',
        template: '{"id":"example_id"}',
      }),
    });
    vi.stubGlobal('fetch', fetchMock);

    const res = await api.getSchemaTemplate(1042);
    expect(res.schemaId).toBe(1042);
    expect(res.template).toBe('{"id":"example_id"}');

    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/schemas/1042/template'),
      expect.any(Object)
    );
  });

  it('client API calls correct schema validate endpoint', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({
        valid: true,
        errors: [],
      }),
    });
    vi.stubGlobal('fetch', fetchMock);

    const res = await api.validateSchemaPayload(1042, '{"id":"example_id"}');
    expect(res.valid).toBe(true);
    expect(res.errors).toEqual([]);

    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/schemas/1042/validate'),
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ payload: '{"id":"example_id"}' }),
      })
    );
  });
});
