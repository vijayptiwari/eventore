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

export default function StreamInspectorLagTab({
  groups,
  selectedGroup,
  onSelectedGroupChange,
  lagTopic,
  onLagTopicChange,
  onRefreshLag,
  lag,
}: Props) {
  return (
    <div className="card">
      <div className="form-grid">
        <div className="form-row">
          <label>Consumer group</label>
          <select value={selectedGroup} onChange={(e) => onSelectedGroupChange(e.target.value)}>
            <option value="">Select group...</option>
            {groups?.map((g) => (
              <option key={g.groupId} value={g.groupId}>
                {g.groupId}
              </option>
            ))}
          </select>
        </div>
        <div className="form-row">
          <label>Topic filter (optional)</label>
          <input value={lagTopic} onChange={(e) => onLagTopicChange(e.target.value)} />
        </div>
      </div>
      <button type="button" className="secondary" onClick={onRefreshLag} disabled={!selectedGroup}>
        Refresh lag
      </button>
      <table>
        <thead>
          <tr>
            <th>Topic</th>
            <th>Partition</th>
            <th>Offset</th>
            <th>Log end</th>
            <th>Lag</th>
          </tr>
        </thead>
        <tbody>
          {lag?.map((row) => (
            <tr key={`${row.topic}-${row.partition ?? 'all'}`}>
              <td>{row.topic}</td>
              <td>{row.partition ?? '—'}</td>
              <td>{row.offset ?? '—'}</td>
              <td>{row.logEndOffset ?? '—'}</td>
              <td className={row.lag > 0 ? 'lag-warn' : ''}>{row.lag}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
