import { useMutation, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../api/client';
import type { KafkaAclEntry } from '../api/kafkaAdminTypes';

const emptyAcl = (): KafkaAclEntry => ({
  resourceType: 'TOPIC',
  resourceName: '',
  patternType: 'LITERAL',
  principal: 'User:',
  host: '*',
  operation: 'READ',
  permissionType: 'ALLOW',
});

interface Props {
  connectionId: string;
}

export default function KafkaAclAdmin({ connectionId }: Props) {
  const [aclFilterType, setAclFilterType] = useState('TOPIC');
  const [aclFilterName, setAclFilterName] = useState('');
  const [aclForm, setAclForm] = useState<KafkaAclEntry>(emptyAcl());
  const [aclEditOld, setAclEditOld] = useState<KafkaAclEntry | null>(null);

  const { data: acls, refetch: refetchAcls } = useQuery({
    queryKey: ['kafka-acls', connectionId, aclFilterType, aclFilterName],
    queryFn: () =>
      api.kafkaListAcls(connectionId, {
        resourceType: aclFilterType || undefined,
        resourceName: aclFilterName || undefined,
      }),
  });

  const createAclMutation = useMutation({
    mutationFn: () => api.kafkaCreateAcl(connectionId, aclForm),
    onSuccess: () => refetchAcls(),
  });

  const deleteAclMutation = useMutation({
    mutationFn: (entry: KafkaAclEntry) => api.kafkaDeleteAcl(connectionId, entry),
    onSuccess: () => refetchAcls(),
  });

  const replaceAclMutation = useMutation({
    mutationFn: () => {
      if (!aclEditOld) throw new Error('Select ACL to edit');
      return api.kafkaReplaceAcl(connectionId, { oldBinding: aclEditOld, newBinding: aclForm });
    },
    onSuccess: () => {
      setAclEditOld(null);
      refetchAcls();
    },
  });

  return (
    <div className="card">
      <h3>ACLs</h3>
      <div className="form-grid">
        <div className="form-row">
          <label>Filter resource type</label>
          <select value={aclFilterType} onChange={(e) => setAclFilterType(e.target.value)}>
            <option value="">Any</option>
            <option value="TOPIC">TOPIC</option>
            <option value="GROUP">GROUP</option>
            <option value="CLUSTER">CLUSTER</option>
            <option value="TRANSACTIONAL_ID">TRANSACTIONAL_ID</option>
          </select>
        </div>
        <div className="form-row">
          <label>Filter resource name</label>
          <input value={aclFilterName} onChange={(e) => setAclFilterName(e.target.value)} />
        </div>
      </div>
      <button type="button" className="secondary" onClick={() => refetchAcls()}>
        Refresh ACLs
      </button>
      <table>
        <thead>
          <tr>
            <th>Resource</th>
            <th>Principal</th>
            <th>Op</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {acls?.map((a, i) => (
            <tr key={`${a.resourceName}-${a.principal}-${i}`}>
              <td>
                {a.resourceType}:{a.resourceName}
              </td>
              <td>{a.principal}</td>
              <td>
                {a.operation} ({a.permissionType})
              </td>
              <td>
                <button
                  type="button"
                  className="secondary"
                  onClick={() => {
                    setAclEditOld(a);
                    setAclForm({ ...a });
                  }}
                >
                  Edit
                </button>{' '}
                <button
                  type="button"
                  className="secondary"
                  onClick={() => {
                    if (window.confirm(`Remove ACL for ${a.principal} on ${a.resourceType}:${a.resourceName}?`)) {
                      deleteAclMutation.mutate(a);
                    }
                  }}
                >
                  Remove
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <h4>{aclEditOld ? 'Edit ACL (replace)' : 'Add ACL'}</h4>
      <div className="form-grid">
        <div className="form-row">
          <label>Resource type</label>
          <input
            value={aclForm.resourceType}
            onChange={(e) => setAclForm({ ...aclForm, resourceType: e.target.value })}
          />
        </div>
        <div className="form-row">
          <label>Resource name</label>
          <input
            value={aclForm.resourceName}
            onChange={(e) => setAclForm({ ...aclForm, resourceName: e.target.value })}
          />
        </div>
        <div className="form-row">
          <label>Pattern</label>
          <select
            value={aclForm.patternType ?? 'LITERAL'}
            onChange={(e) => setAclForm({ ...aclForm, patternType: e.target.value })}
          >
            <option value="LITERAL">LITERAL</option>
            <option value="PREFIXED">PREFIXED</option>
          </select>
        </div>
        <div className="form-row">
          <label>Principal</label>
          <input
            value={aclForm.principal}
            onChange={(e) => setAclForm({ ...aclForm, principal: e.target.value })}
            placeholder="User:alice"
          />
        </div>
        <div className="form-row">
          <label>Host</label>
          <input value={aclForm.host ?? '*'} onChange={(e) => setAclForm({ ...aclForm, host: e.target.value })} />
        </div>
        <div className="form-row">
          <label>Operation</label>
          <input
            value={aclForm.operation}
            onChange={(e) => setAclForm({ ...aclForm, operation: e.target.value })}
          />
        </div>
        <div className="form-row">
          <label>Permission</label>
          <select
            value={aclForm.permissionType ?? 'ALLOW'}
            onChange={(e) => setAclForm({ ...aclForm, permissionType: e.target.value })}
          >
            <option value="ALLOW">ALLOW</option>
            <option value="DENY">DENY</option>
          </select>
        </div>
      </div>
      {aclEditOld ? (
        <button
          type="button"
          disabled={replaceAclMutation.isPending}
          onClick={() => replaceAclMutation.mutate()}
        >
          Save (replace ACL)
        </button>
      ) : (
        <button type="button" disabled={createAclMutation.isPending} onClick={() => createAclMutation.mutate()}>
          Add ACL
        </button>
      )}
      {aclEditOld && (
        <button type="button" className="secondary" onClick={() => setAclEditOld(null)}>
          Cancel edit
        </button>
      )}
      {createAclMutation.isError && (
        <p className="stream-error">{String(createAclMutation.error)}</p>
      )}
      {deleteAclMutation.isError && (
        <p className="stream-error">{String(deleteAclMutation.error)}</p>
      )}
      {replaceAclMutation.isError && (
        <p className="stream-error">{String(replaceAclMutation.error)}</p>
      )}
    </div>
  );
}
