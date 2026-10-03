import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { computeLagMetrics, getLagSeverity } from './components/StreamInspectorLagTab';

describe('REQ-112: Visual Topology & Consumer Lag Heatmap UI and Contract Tests', () => {
  describe('computeLagMetrics calculation and severity logic', () => {
    it('returns default zeroed metrics for undefined or empty lag lists', () => {
      const emptyRes = computeLagMetrics([]);
      expect(emptyRes.totalLag).toBe(0);
      expect(emptyRes.maxLag).toBe(0);
      expect(emptyRes.healthStatus.label).toBe('HEALTHY');
      expect(emptyRes.isHighSkew).toBe(false);

      const undefRes = computeLagMetrics(undefined);
      expect(undefRes.totalLag).toBe(0);
      expect(undefRes.maxLagPartition).toBe('—');
    });

    it('correctly aggregates healthy zero-lag partitions', () => {
      const lag = [
        { topic: 'orders', partition: 0, offset: 100, logEndOffset: 100, lag: 0 },
        { topic: 'orders', partition: 1, offset: 250, logEndOffset: 250, lag: 0 },
      ];
      const res = computeLagMetrics(lag);
      expect(res.totalLag).toBe(0);
      expect(res.maxLag).toBe(0);
      expect(res.laggingCount).toBe(0);
      expect(res.totalPartitions).toBe(2);
      expect(res.healthStatus.label).toBe('HEALTHY');
      expect(res.healthStatus.class).toBe('lag-status-healthy');
      expect(res.isHighSkew).toBe(false);
    });

    it('identifies low lag with even distribution', () => {
      const lag = [
        { topic: 'orders', partition: 0, offset: 95, logEndOffset: 100, lag: 5 },
        { topic: 'orders', partition: 1, offset: 90, logEndOffset: 100, lag: 10 },
      ];
      const res = computeLagMetrics(lag);
      expect(res.totalLag).toBe(15);
      expect(res.maxLag).toBe(10);
      expect(res.maxLagPartition).toBe('1');
      expect(res.laggingCount).toBe(2);
      expect(res.healthStatus.label).toBe('LOW LAG');
      expect(res.healthStatus.class).toBe('lag-status-low');
      expect(res.isHighSkew).toBe(false);
    });

    it('flags warning when partition lag is between 101 and 1000 and detects partition skew', () => {
      const lag = [
        { topic: 'telemetry', partition: 0, offset: 1000, logEndOffset: 1000, lag: 0 },
        { topic: 'telemetry', partition: 1, offset: 700, logEndOffset: 1000, lag: 300 },
        { topic: 'telemetry', partition: 2, offset: 990, logEndOffset: 1000, lag: 10 },
      ];
      const res = computeLagMetrics(lag);
      expect(res.totalLag).toBe(310);
      expect(res.maxLag).toBe(300);
      expect(res.maxLagPartition).toBe('1');
      expect(res.skew).toBe(300);
      expect(res.isHighSkew).toBe(true);
      expect(res.healthStatus.label).toBe('WARNING');
      expect(res.healthStatus.class).toBe('lag-status-warning');
    });

    it('flags critical when any partition lag exceeds 1000', () => {
      const lag = [
        { topic: 'payments', partition: 0, offset: 0, logEndOffset: 2500, lag: 2500 },
        { topic: 'payments', partition: 1, offset: 2490, logEndOffset: 2500, lag: 10 },
      ];
      const res = computeLagMetrics(lag);
      expect(res.totalLag).toBe(2510);
      expect(res.maxLag).toBe(2500);
      expect(res.maxLagPartition).toBe('0');
      expect(res.healthStatus.label).toBe('CRITICAL');
      expect(res.healthStatus.class).toBe('lag-status-critical');
      expect(res.isHighSkew).toBe(true);
    });

    it('getLagSeverity classifies thresholds accurately', () => {
      expect(getLagSeverity(0)).toBe('healthy');
      expect(getLagSeverity(-1)).toBe('healthy');
      expect(getLagSeverity(1)).toBe('low');
      expect(getLagSeverity(100)).toBe('low');
      expect(getLagSeverity(101)).toBe('warning');
      expect(getLagSeverity(1000)).toBe('warning');
      expect(getLagSeverity(1001)).toBe('critical');
      expect(getLagSeverity(50000)).toBe('critical');
    });
  });

  describe('StreamInspectorLagTab UI component contracts', () => {
    const lagTabPath = join(__dirname, 'components', 'StreamInspectorLagTab.tsx');
    const lagTabContent = readFileSync(lagTabPath, 'utf-8');

    it('contains KPI summary cards and skew alert markers', () => {
      expect(lagTabContent).toContain('data-testid="lag-metrics-summary"');
      expect(lagTabContent).toContain('data-testid="kpi-total-lag"');
      expect(lagTabContent).toContain('data-testid="kpi-max-lag"');
      expect(lagTabContent).toContain('data-testid="kpi-health-status"');
      expect(lagTabContent).toContain('data-testid="kpi-partition-skew"');
      expect(lagTabContent).toContain('data-testid="lag-skew-alert"');
    });

    it('contains interactive Partition Lag Heatmap grid and cell severity classes', () => {
      expect(lagTabContent).toContain('data-testid="lag-heatmap-section"');
      expect(lagTabContent).toContain('Partition Lag Heatmap');
      expect(lagTabContent).toContain('lag-heatmap-grid');
      expect(lagTabContent).toContain('lag-heatmap-cell');
      expect(lagTabContent).toContain('lag-cell-${severity}');
      expect(lagTabContent).toContain('togglePartition');
      expect(lagTabContent).toContain('Clear Filter');
    });

    it('renders proportional progress bars for consumption tracking', () => {
      expect(lagTabContent).toContain('lag-progress-track');
      expect(lagTabContent).toContain('lag-progress-fill');
      expect(lagTabContent).toContain('Consumption Progress');
    });
  });

  describe('StreamInspectorOverviewTab visual topology contracts', () => {
    const overviewTabPath = join(__dirname, 'components', 'StreamInspectorOverviewTab.tsx');
    const overviewContent = readFileSync(overviewTabPath, 'utf-8');

    it('contains cluster summary KPI grid and broker topology card', () => {
      expect(overviewContent).toContain('data-testid="cluster-topology-kpi-grid"');
      expect(overviewContent).toContain('data-testid="kpi-cluster-id"');
      expect(overviewContent).toContain('data-testid="kpi-broker-count"');
      expect(overviewContent).toContain('data-testid="kpi-controller-node"');
      expect(overviewContent).toContain('data-testid="kpi-cluster-status"');
    });

    it('renders interactive Broker Nodes Grid and controller leader badge', () => {
      expect(overviewContent).toContain('data-testid="broker-nodes-grid"');
      expect(overviewContent).toContain('broker-node-card');
      expect(overviewContent).toContain('is-controller');
      expect(overviewContent).toContain('controller-badge');
      expect(overviewContent).toContain('👑 Controller');
      expect(overviewContent).toContain('pulse-dot');
    });

    it('renders protocol feature capability chips', () => {
      expect(overviewContent).toContain('data-testid="feature-capabilities-grid"');
      expect(overviewContent).toContain('feature-chip');
    });
  });

  describe('StreamInspectorTopicsTab partition distribution matrix contracts', () => {
    const topicsTabPath = join(__dirname, 'components', 'StreamInspectorTopicsTab.tsx');
    const topicsContent = readFileSync(topicsTabPath, 'utf-8');

    it('renders partition matrix section and ISR replication health tags', () => {
      expect(topicsContent).toContain('data-testid="topic-matrix-section"');
      expect(topicsContent).toContain('data-testid="topic-topology-kpi-grid"');
      expect(topicsContent).toContain('data-testid="kpi-topic-partitions"');
      expect(topicsContent).toContain('data-testid="kpi-topic-replication"');
      expect(topicsContent).toContain('data-testid="kpi-isr-health"');
      expect(topicsContent).toContain('data-testid="topic-partitions-table"');
      expect(topicsContent).toContain('partition-health-tag');
      expect(topicsContent).toContain('under-replicated');
    });
  });

  describe('index.css styling tokens for REQ-112', () => {
    const cssPath = join(__dirname, 'index.css');
    const cssContent = readFileSync(cssPath, 'utf-8');

    it('defines styles for heatmap, broker nodes, and status indicators', () => {
      expect(cssContent).toContain('.lag-metrics-summary');
      expect(cssContent).toContain('.lag-kpi-card');
      expect(cssContent).toContain('.lag-status-healthy');
      expect(cssContent).toContain('.lag-status-critical');
      expect(cssContent).toContain('.lag-heatmap-grid');
      expect(cssContent).toContain('.lag-heatmap-cell');
      expect(cssContent).toContain('.broker-nodes-grid');
      expect(cssContent).toContain('.broker-node-card.is-controller');
      expect(cssContent).toContain('.controller-badge');
      expect(cssContent).toContain('.topic-matrix-section');
    });
  });
});
