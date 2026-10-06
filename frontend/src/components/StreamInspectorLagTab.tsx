import { useState, useMemo } from 'react';

interface LagRow {
  topic: string;
  partition?: number | string;
  offset?: number | string;
  logEndOffset?: number | string;
  lag: number;
}

interface GroupRow {
  groupId: string;
}

interface Props {
  groups: GroupRow[] | undefined;
  selectedGroup: string;
  onSelectedGroupChange: (groupId: string) => void;
  lagTopic: string;
  onLagTopicChange: (value: string) => void;
  onRefreshLag: () => void;
  lag: LagRow[] | undefined;
}

export function getLagSeverity(lag: number): 'healthy' | 'low' | 'warning' | 'critical' {
  if (lag <= 0) return 'healthy';
  if (lag <= 100) return 'low';
  if (lag <= 1000) return 'warning';
  return 'critical';
}

export function computeLagMetrics(lag: LagRow[] | undefined) {
  if (!lag || lag.length === 0) {
    return {
      totalLag: 0,
      maxLag: 0,
      minLag: 0,
      maxLagPartition: '—',
      laggingCount: 0,
      totalPartitions: 0,
      skew: 0,
      isHighSkew: false,
      healthStatus: { label: 'NO DATA', class: 'lag-status-unknown', icon: '—' },
    };
  }

  const lags = lag.map((r) => Math.max(0, r.lag || 0));
  const totalLag = lags.reduce((sum, val) => sum + val, 0);
  const maxLag = Math.max(...lags);
  const minLag = Math.min(...lags);
  const maxLagRow = lag.find((r) => (r.lag || 0) === maxLag);
  const laggingCount = lags.filter((l) => l > 0).length;
  const totalPartitions = lag.length;
  const skew = maxLag - minLag;
  const isHighSkew = totalPartitions > 1 && skew > 100;

  let healthStatus = { label: 'HEALTHY', class: 'lag-status-healthy', icon: '●' };
  if (maxLag > 1000) {
    healthStatus = { label: 'CRITICAL', class: 'lag-status-critical', icon: '⚠' };
  } else if (maxLag > 100) {
    healthStatus = { label: 'WARNING', class: 'lag-status-warning', icon: '▲' };
  } else if (totalLag > 0) {
    healthStatus = { label: 'LOW LAG', class: 'lag-status-low', icon: '●' };
  }

  return {
    totalLag,
    maxLag,
    minLag,
    maxLagPartition: maxLagRow?.partition !== undefined ? String(maxLagRow.partition) : '0',
    laggingCount,
    totalPartitions,
    skew,
    isHighSkew,
    healthStatus,
  };
}

export default function StreamInspectorLagTab({
  groups,
  selectedGroup,
  onSelectedGroupChange,
  lagTopic,
  onLagTopicChange,
  onRefreshLag,
  lag,
}: Props) {
  const [selectedPartition, setSelectedPartition] = useState<string | number | null>(null);

  const metrics = useMemo(() => computeLagMetrics(lag), [lag]);

  const filteredLag = useMemo(() => {
    if (!lag) return [];
    if (selectedPartition === null) return lag;
    return lag.filter((r) => String(r.partition) === String(selectedPartition));
  }, [lag, selectedPartition]);

  const togglePartition = (partition?: string | number) => {
    if (partition === undefined) return;
    if (selectedPartition !== null && String(selectedPartition) === String(partition)) {
      setSelectedPartition(null);
    } else {
      setSelectedPartition(partition);
    }
  };

  return (
    <div className="card">
      <div className="form-grid">
        <div className="form-row">
          <label htmlFor="lag-consumer-group-select">Consumer group</label>
          <select
            id="lag-consumer-group-select"
            value={selectedGroup}
            onChange={(e) => onSelectedGroupChange(e.target.value)}
          >
            <option value="">Select group...</option>
            {groups?.map((g) => (
              <option key={g.groupId} value={g.groupId}>
                {g.groupId}
              </option>
            ))}
          </select>
        </div>
        <div className="form-row">
          <label htmlFor="lag-topic-filter-input">Topic filter (optional)</label>
          <input
            id="lag-topic-filter-input"
            value={lagTopic}
            onChange={(e) => onLagTopicChange(e.target.value)}
          />
        </div>
      </div>
      <button
        id="lag-refresh-btn"
        type="button"
        className="secondary"
        onClick={onRefreshLag}
        disabled={!selectedGroup}
      >
        Refresh lag
      </button>

      {lag && lag.length > 0 && (
        <>
          {/* Summary KPI Cards */}
          <div className="lag-metrics-summary" data-testid="lag-metrics-summary">
            <div className="lag-kpi-card" data-testid="kpi-total-lag">
              <span className="lag-kpi-label">Total Consumer Lag</span>
              <span className="lag-kpi-val">{metrics.totalLag.toLocaleString()} msgs</span>
              <span className="lag-kpi-sub">
                {metrics.laggingCount} of {metrics.totalPartitions} partitions lagging
              </span>
            </div>

            <div className="lag-kpi-card" data-testid="kpi-max-lag">
              <span className="lag-kpi-label">Max Partition Lag</span>
              <span className="lag-kpi-val">
                P#{metrics.maxLagPartition}: {metrics.maxLag.toLocaleString()}
              </span>
              <span className="lag-kpi-sub">Peak single-partition deficit</span>
            </div>

            <div className="lag-kpi-card" data-testid="kpi-health-status">
              <span className="lag-kpi-label">Health Status</span>
              <div>
                <span className={`lag-status-badge ${metrics.healthStatus.class}`}>
                  {metrics.healthStatus.icon} {metrics.healthStatus.label}
                </span>
              </div>
              <span className="lag-kpi-sub">Across all partitions</span>
            </div>

            <div className="lag-kpi-card" data-testid="kpi-partition-skew">
              <span className="lag-kpi-label">Partition Skew</span>
              <span className="lag-kpi-val">{metrics.skew.toLocaleString()} msgs</span>
              <span className="lag-kpi-sub">
                {metrics.isHighSkew ? '⚠️ High variance detected' : 'Evenly distributed'}
              </span>
            </div>
          </div>

          {/* Skew Warning Alert */}
          {metrics.isHighSkew && (
            <div className="lag-skew-banner" data-testid="lag-skew-alert">
              <span>⚠️</span>
              <span>
                <strong>Partition Skew Warning:</strong> Message lag varies by{' '}
                {metrics.skew.toLocaleString()} messages between partitions. Check for hot keys or
                uneven consumer thread processing.
              </span>
            </div>
          )}

          {/* Interactive Partition Lag Heatmap Grid */}
          <div className="lag-heatmap-section" data-testid="lag-heatmap-section">
            <div className="lag-heatmap-header">
              <span className="lag-heatmap-title">
                <span>🔥 Partition Lag Heatmap</span>
                {selectedPartition !== null && (
                  <button
                    type="button"
                    className="lag-filter-reset"
                    onClick={() => setSelectedPartition(null)}
                  >
                    Clear Filter (P#{selectedPartition})
                  </button>
                )}
              </span>

              <div className="lag-heatmap-legend">
                <span className="lag-legend-item">
                  <span className="lag-legend-dot healthy" /> 0 (Healthy)
                </span>
                <span className="lag-legend-item">
                  <span className="lag-legend-dot low" /> 1–100 (Low)
                </span>
                <span className="lag-legend-item">
                  <span className="lag-legend-dot warning" /> 101–1k (Warning)
                </span>
                <span className="lag-legend-item">
                  <span className="lag-legend-dot critical" /> &gt;1k (Critical)
                </span>
              </div>
            </div>

            <div className="lag-heatmap-grid" role="grid" aria-label="Partition lag heatmap">
              {lag.map((row) => {
                const p = row.partition ?? 0;
                const severity = getLagSeverity(row.lag || 0);
                const isSelected = selectedPartition !== null && String(selectedPartition) === String(p);

                const offsetNum = Number(row.offset);
                const logEndNum = Number(row.logEndOffset);
                const progressPct =
                  !isNaN(offsetNum) && !isNaN(logEndNum) && logEndNum > 0
                    ? Math.min(100, Math.max(0, Math.round((offsetNum / logEndNum) * 100)))
                    : 100;

                return (
                  <div
                    key={`${row.topic}-${p}`}
                    role="gridcell"
                    tabIndex={0}
                    aria-label={`Partition ${p}: Lag ${row.lag} messages`}
                    className={`lag-heatmap-cell lag-cell-${severity} ${isSelected ? 'is-selected' : ''}`}
                    onClick={() => togglePartition(p)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault();
                        togglePartition(p);
                      }
                    }}
                    data-testid={`heatmap-cell-${p}`}
                  >
                    <div className="lag-cell-top">
                      <span>P#{p}</span>
                      <span>
                        {severity === 'healthy' && '✓'}
                        {severity === 'low' && '⚡'}
                        {severity === 'warning' && '▲'}
                        {severity === 'critical' && '⚠'}
                      </span>
                    </div>

                    <div className="lag-cell-val">{(row.lag || 0).toLocaleString()}</div>

                    <div className="lag-cell-sub">
                      Offset: {row.offset ?? '—'} / {row.logEndOffset ?? '—'}
                    </div>

                    <div className="lag-progress-track" title={`${progressPct}% consumed`}>
                      <div
                        className={`lag-progress-fill ${severity}`}
                        style={{ width: `${progressPct}%` }}
                      />
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </>
      )}

      {/* Detailed Partition Lag Table */}
      <table data-testid="lag-table">
        <thead>
          <tr>
            <th>Topic</th>
            <th>Partition</th>
            <th>Offset</th>
            <th>Log end</th>
            <th>Lag</th>
            <th style={{ minWidth: '120px' }}>Consumption Progress</th>
          </tr>
        </thead>
        <tbody>
          {filteredLag.map((row) => {
            const p = row.partition ?? '—';
            const offsetNum = Number(row.offset);
            const logEndNum = Number(row.logEndOffset);
            const progressPct =
              !isNaN(offsetNum) && !isNaN(logEndNum) && logEndNum > 0
                ? Math.min(100, Math.max(0, Math.round((offsetNum / logEndNum) * 100)))
                : 100;
            const severity = getLagSeverity(row.lag || 0);

            return (
              <tr
                key={`${row.topic}-${p}`}
                className={selectedPartition !== null && String(selectedPartition) === String(p) ? 'selected-row' : ''}
              >
                <td>{row.topic}</td>
                <td>
                  <strong>P#{p}</strong>
                </td>
                <td>{row.offset ?? '—'}</td>
                <td>{row.logEndOffset ?? '—'}</td>
                <td className={row.lag > 0 ? 'lag-warn' : ''}>
                  {(row.lag || 0).toLocaleString()} msgs
                </td>
                <td>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                    <div className="lag-progress-track" style={{ flex: 1, margin: 0 }}>
                      <div
                        className={`lag-progress-fill ${severity}`}
                        style={{ width: `${progressPct}%` }}
                      />
                    </div>
                    <span style={{ fontSize: '0.72rem', color: '#94a3b8', width: '35px' }}>
                      {progressPct}%
                    </span>
                  </div>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

