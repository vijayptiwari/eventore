interface ShardRow {
  shardId: string;
  hashKeyRange?: string;
  sequenceNumberRange?: string;
}

interface Props {
  streamName: string;
  shards: ShardRow[] | undefined;
  isLoading: boolean;
  error: unknown;
}

export default function StreamInspectorShardsTab({
  streamName,
  shards,
  isLoading,
  error,
}: Props) {
  return (
    <div className="card">
      <h3>Kinesis shards — {streamName}</h3>
      {isLoading && <p>Loading shards...</p>}
      {error && <p className="stream-error">{String(error)}</p>}
      {!isLoading && !error && (
        <table>
          <thead>
            <tr>
              <th>Shard ID</th>
              <th>Hash key range</th>
              <th>Sequence number range</th>
            </tr>
          </thead>
          <tbody>
            {shards?.map((shard) => (
              <tr key={shard.shardId}>
                <td>{shard.shardId}</td>
                <td>{shard.hashKeyRange ?? '—'}</td>
                <td>{shard.sequenceNumberRange ?? '—'}</td>
              </tr>
            ))}
            {!shards?.length && (
              <tr>
                <td colSpan={3}>No shards returned for this stream.</td>
              </tr>
            )}
          </tbody>
        </table>
      )}
    </div>
  );
}
