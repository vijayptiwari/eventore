import { describe, expect, it } from 'vitest';
import { hasInspectFeature } from './inspectFeatures';

describe('hasInspectFeature', () => {
  it('matches exact capability tokens only', () => {
    const features = ['lag', 'backlog', 'message-search', 'planned'];
    expect(hasInspectFeature(features, 'lag')).toBe(true);
    expect(hasInspectFeature(features, 'backlog')).toBe(true);
    expect(hasInspectFeature(features, 'message-search')).toBe(true);
  });

  it('does not match substrings inside other tokens', () => {
    expect(hasInspectFeature(['planned'], 'lag')).toBe(false);
    expect(hasInspectFeature(['kafka-full'], 'lag')).toBe(false);
    expect(hasInspectFeature(['full-inspect'], 'inspect')).toBe(false);
  });

  it('returns false for empty or missing feature lists', () => {
    expect(hasInspectFeature(undefined, 'lag')).toBe(false);
    expect(hasInspectFeature([], 'lag')).toBe(false);
  });
});
