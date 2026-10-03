import { describe, it, expect, vi, beforeEach } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

describe('REQ-109: Time-Travel Message Replay & Offset Rewind UI and Contract', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('LiveViewPanel source contains time-travel replay controls and timestamp presets', () => {
    const filePath = join(__dirname, 'components', 'LiveViewPanel.tsx');
    const content = readFileSync(filePath, 'utf-8');

    // Verifies replay mode selection header and buttons
    expect(content).toContain('Starting Point & Time-Travel Replay');
    expect(content).toContain('Latest (Real-time)');
    expect(content).toContain('Earliest (Beginning)');
    expect(content).toContain('Timestamp (Time-Travel)');
    expect(content).toContain('Specific Offset');

    // Verifies timestamp presets and inputs
    expect(content).toContain('Replay From Timestamp');
    expect(content).toContain('5m ago');
    expect(content).toContain('1h ago');
    expect(content).toContain('1d ago');

    // Verifies offset input
    expect(content).toContain('Starting Partition Offset');

    // Verifies startLiveView parameters include replay options
    expect(content).toContain('replayMode');
    expect(content).toContain('replayTimestamp: replayMode === \'TIMESTAMP\' ? replayTimestamp : undefined');
    expect(content).toContain('replayOffset: replayMode === \'OFFSET\' && replayOffset ? parseInt(replayOffset, 10) : undefined');
  });

  it('StreamInspectorSearchTab source contains time-travel timestamp search input', () => {
    const filePath = join(__dirname, 'components', 'StreamInspectorSearchTab.tsx');
    const content = readFileSync(filePath, 'utf-8');

    expect(content).toContain('From Timestamp (Time-Travel)');
    expect(content).toContain('searchTimestamp');
    expect(content).toContain('onSearchTimestampChange');
  });

  it('StreamWorkspaceContext passes replay parameters in START_LIVE_VIEW WebSocket command', () => {
    const filePath = join(__dirname, 'stream', 'StreamWorkspaceContext.tsx');
    const content = readFileSync(filePath, 'utf-8');

    expect(content).toContain('type: \'START_LIVE_VIEW\'');
    expect(content).toContain('replayMode: config.replayMode || undefined');
    expect(content).toContain('replayTimestamp: config.replayTimestamp || undefined');
    expect(content).toContain('replayOffset: config.replayOffset || undefined');
  });
});
