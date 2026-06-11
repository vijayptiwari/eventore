import type { ProtocolType } from '../api/types';

interface GroupRow {
  groupId: string;
  state?: string;
  memberCount?: number;
}

interface Props {
  protocol: ProtocolType;
  groups: GroupRow[] | undefined;
  selectedGroup: string;
  onSelectedGroupChange: (groupId: string) => void;
  groupDetail: unknown;
}

export default function StreamInspectorGroupsTab({
  protocol,
  groups,
  selectedGroup,
  onSelectedGroupChange,
  groupDetail,
}: Props) {
  return (
    <div className="card">
      {protocol === 'RABBITMQ' && (
        <p className="inspector-meta">Queue list from the RabbitMQ management API.</p>
      )}
      {protocol !== 'KAFKA' && protocol !== 'PULSAR' && protocol !== 'RABBITMQ' && (
        <p>Groups and subscriptions vary by protocol — see Overview for supported features.</p>
      )}
      <table>
        <thead>
          <tr>
            <th>Group</th>
            <th>State</th>
            <th>Members</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {groups?.map((g) => (
            <tr key={g.groupId}>
              <td>{g.groupId}</td>
              <td>{g.state}</td>
              <td>{g.memberCount ?? '—'}</td>
              <td>
                <button
                  type="button"
                  className="secondary"
                  onClick={() => onSelectedGroupChange(g.groupId)}
                >
                  Details
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {groupDetail && (
        <pre className="inspector-pre">{JSON.stringify(groupDetail, null, 2)}</pre>
      )}
    </div>
  );
}
