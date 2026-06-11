import { describe, expect, it } from 'vitest';
import { hasInspectFeature } from './inspectFeatures';

describe('StreamInspector capability gating (FEAT-3.3)', () => {
  it('AC-7: MQTT without message-search should not show search tab', () => {
    const feats = ['cluster', 'topics'];
    expect(hasInspectFeature(feats, 'message-search')).toBe(false);
  });

  it('AC-2: lag tab from backlog token without Kafka hardcode', () => {
    const feats = ['backlog'];
    expect(hasInspectFeature(feats, 'lag')).toBe(false);
    expect(hasInspectFeature(feats, 'backlog')).toBe(true);
  });

  it('AC-3: RabbitMQ groups tab from queues feature', () => {
    const feats = ['queues', 'queue-detail', 'message-search'];
    expect(hasInspectFeature(feats, 'queues')).toBe(true);
    expect(hasInspectFeature(feats, 'subscriptions')).toBe(false);
  });

  it('AC-1: Kafka message-search capability alone enables search (no protocol override)', () => {
    const feats = ['cluster', 'topics', 'message-search'];
    expect(hasInspectFeature(feats, 'message-search')).toBe(true);
  });

  it('AC-5: RabbitMQ without subscriptions should not expose subscriptions token', () => {
    const feats = ['queues', 'queue-detail', 'lag', 'message-search'];
    expect(hasInspectFeature(feats, 'subscriptions')).toBe(false);
    expect(hasInspectFeature(feats, 'queues')).toBe(true);
  });
});
