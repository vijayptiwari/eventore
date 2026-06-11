import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api, canAction, PROTOCOL_DEFAULTS } from '../api/client';
import ConnectionWizardDialog from '../components/ConnectionWizardDialog';
import {
  applyPresetToForm,
  defaultProtocol,
  emptyForm,
  presetKey,
  PROTOCOL_EXTRA_FIELDS,
} from '../connections/connectionFormShared';
import { useAppConfig } from '../hooks/useAppConfig';
import { useControlPlane } from '../hooks/useControlPlane';
import type { StreamPlatformPreset } from '../api/platformTypes';
import type { ConnectionProfile, ProtocolType } from '../api/types';
export default function ConnectionsPage() {
  const { data: config } = useAppConfig();
  const { connectionProtocols: controlProtocols } = useControlPlane();
  const [searchParams, setSearchParams] = useSearchParams();
  const supportedProtocols = useMemo(
    () =>
      controlProtocols.length > 0
        ? controlProtocols
        : (config?.supportedProtocols ?? []),
    [controlProtocols, config?.supportedProtocols],
  );
  const queryClient = useQueryClient();
  const { data: connections, isLoading } = useQuery({
    queryKey: ['connections'],
    queryFn: api.listConnections,
  });
  const { data: platforms } = useQuery({
    queryKey: ['platforms'],
    queryFn: api.listPlatforms,
  });
  const visiblePlatforms = useMemo(
    () =>
      platforms?.filter((p) => supportedProtocols.includes(p.protocol)) ?? [],
    [platforms, supportedProtocols],
  );
  const [wizardOpen, setWizardOpen] = useState(false);
  const [form, setForm] = useState<ConnectionProfile>(() =>
    emptyForm(defaultProtocol(supportedProtocols)),
  );
  const [selectedPresetKey, setSelectedPresetKey] = useState('');
  const canManage = canAction(config?.allowedActions, 'MANAGE_CONNECTIONS');

  useEffect(() => {
    if (searchParams.get('wizard') === '1') {
      setWizardOpen(true);
      setSearchParams({}, { replace: true });
    }
  }, [searchParams, setSearchParams]);

  useEffect(() => {
    if (!supportedProtocols.length) return;
    setForm((f) => {
      if (supportedProtocols.includes(f.protocol)) return f;
      const protocol = defaultProtocol(supportedProtocols);
      return { ...emptyForm(protocol), name: f.name };
    });
  }, [supportedProtocols]);

  const createMutation = useMutation({
    mutationFn: api.createConnection,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['connections'] });
      setForm(emptyForm(defaultProtocol(supportedProtocols)));
    },
  });

  const deleteMutation = useMutation({
    mutationFn: api.deleteConnection,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['connections'] }),
  });

  type RowValidationState =
    | { status: 'pending' }
    | { status: 'success'; result: string }
    | { status: 'error'; message: string };

  function renderValidationStatus(state: RowValidationState | undefined) {
    if (!state) return null;
    if (state.status === 'pending') {
      return <span className="inspector-meta"> Testing…</span>;
    }
    if (state.status === 'success') {
      return <span className="tag tag-ok"> {state.result}</span>;
    }
    return <span className="stream-error"> {state.message}</span>;
  }

  const [validationById, setValidationById] = useState<Record<string, RowValidationState>>({});

  const testConnection = (id: string) => {
    setValidationById((prev) => ({ ...prev, [id]: { status: 'pending' } }));
    api
      .validateConnection(id)
      .then((data) => {
        setValidationById((prev) => ({
          ...prev,
          [id]: { status: 'success', result: data?.status ?? 'OK' },
        }));
      })
      .catch((err) => {
        setValidationById((prev) => ({
          ...prev,
          [id]: { status: 'error', message: String(err) },
        }));
      });
  };

  const onProtocolChange = (protocol: ProtocolType) => {
    setSelectedPresetKey('');
    setForm((f) => ({
      ...f,
      protocol,
      brokerUrl: PROTOCOL_DEFAULTS[protocol].brokerUrl,
    }));
  };

  const applyPreset = (preset: StreamPlatformPreset) => {
    setSelectedPresetKey(presetKey(preset));
    setForm((f) => applyPresetToForm(f, preset));
  };

  return (
    <div>
      <h1>Connections</h1>
      {canManage && (
        <div className="card">
          <h2>New connection</h2>
          <p className="inspector-meta">
            Use the guided wizard for presets, secret refs, and validate-before-save.
          </p>
          <button type="button" onClick={() => setWizardOpen(true)}>
            Open connection wizard
          </button>
          <details className="wizard-advanced-form">
            <summary>Advanced: quick form</summary>
            <div className="form-grid">
              <div className="form-row">
                <label>Name</label>
                <input
                  value={form.name}
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                />
              </div>
              <div className="form-row">
                <label>Stream platform preset</label>
                <select
                  value={selectedPresetKey}
                  onChange={(e) => {
                    const preset = visiblePlatforms.find((p) => presetKey(p) === e.target.value);
                    if (preset) applyPreset(preset);
                    else setSelectedPresetKey('');
                  }}
                >
                  <option value="">Custom / manual</option>
                  {visiblePlatforms.map((p) => {
                    const key = presetKey(p);
                    return (
                      <option key={key} value={key}>
                        [{p.cloudProvider}] {p.label}
                      </option>
                    );
                  })}
                </select>
              </div>
              <div className="form-row">
                <label>Protocol</label>
                <select
                  value={form.protocol}
                  onChange={(e) => onProtocolChange(e.target.value as ProtocolType)}
                >
                  {supportedProtocols.map((p) => (
                    <option key={p} value={p}>
                      {p}
                    </option>
                  ))}
                </select>
              </div>
              <div className="form-row">
                <label>Broker URL</label>
                <input
                  value={form.brokerUrl}
                  onChange={(e) => setForm({ ...form, brokerUrl: e.target.value })}
                  placeholder={PROTOCOL_DEFAULTS[form.protocol].hint}
                />
              </div>
              {(PROTOCOL_EXTRA_FIELDS[form.protocol] ?? []).map((field) => {
                const current =
                  field.kind === 'property'
                    ? form.properties?.[field.key]
                    : form.credentials?.[field.key];
                const fallback = field.syncBrokerUrl ? form.brokerUrl : (field.defaultValue ?? '');
                return (
                  <div className="form-row" key={`${form.protocol}-${field.key}`}>
                    <label>{field.label}</label>
                    <input
                      type={field.password ? 'password' : 'text'}
                      value={current ?? fallback}
                      onChange={(e) =>
                        setForm((f) => ({
                          ...f,
                          ...(field.kind === 'property'
                            ? { properties: { ...f.properties, [field.key]: e.target.value } }
                            : { credentials: { ...f.credentials, [field.key]: e.target.value } }),
                          ...(field.syncBrokerUrl ? { brokerUrl: e.target.value } : {}),
                        }))
                      }
                    />
                  </div>
                );
              })}
            </div>
            <button
              disabled={!form.name || createMutation.isPending}
              onClick={() => createMutation.mutate(form)}
            >
              Save connection
            </button>
            {createMutation.isError && (
              <p className="stream-error">{String(createMutation.error)}</p>
            )}
          </details>
        </div>
      )}
      <ConnectionWizardDialog
        open={wizardOpen}
        onClose={() => setWizardOpen(false)}
        config={config}
      />
      <div className="card">
        <h2>Saved connections</h2>
        {isLoading && <p>Loading...</p>}
        <table>
          <thead>
            <tr>
              <th>Name</th>
              <th>Protocol</th>
              <th>Broker</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {connections?.map((c) => (
              <tr key={c.id}>
                <td>{c.name}</td>
                <td>
                  <span className="tag">{c.protocol}</span>
                </td>
                <td>{c.brokerUrl}</td>
                <td>
                  <button
                    className="secondary"
                    onClick={() => c.id && testConnection(c.id)}
                    disabled={c.id ? validationById[c.id]?.status === 'pending' : false}
                  >
                    Test
                  </button>{' '}
                  {canManage && (
                    <button
                      className="secondary"
                      onClick={() => {
                        if (c.id && window.confirm(`Delete connection "${c.name}"?`)) {
                          deleteMutation.mutate(c.id);
                        }
                      }}
                      disabled={deleteMutation.isPending}
                    >
                      Delete
                    </button>
                  )}
                  {c.id ? renderValidationStatus(validationById[c.id]) : null}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {!connections?.length && <p>No connections yet.</p>}
      </div>
    </div>
  );
}
