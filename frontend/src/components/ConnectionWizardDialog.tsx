import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useId, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, canAction } from '../api/client';
import type { StreamPlatformPreset } from '../api/platformTypes';
import type { AppConfig, ConnectionProfile, ProtocolType } from '../api/types';
import {
  applyPresetToForm,
  defaultProtocol,
  emptyForm,
  presetKey,
  PROTOCOL_EXTRA_FIELDS,
  protocolGuideUrl,
  SECRETS_DOC_URL,
} from '../connections/connectionFormShared';
import { useControlPlane } from '../hooks/useControlPlane';

const STEPS = ['Preset', 'Credentials', 'Validate', 'Done'] as const;
const TOTAL_STEPS = STEPS.length;

interface Props {
  open: boolean;
  onClose: () => void;
  config: AppConfig | undefined;
}

type ValidationState =
  | { status: 'idle' }
  | { status: 'pending' }
  | { status: 'ok'; message: string }
  | { status: 'error'; message: string };

export default function ConnectionWizardDialog({ open, onClose, config }: Props) {
  const titleId = useId();
  const closeRef = useRef<HTMLButtonElement>(null);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { connectionProtocols: controlProtocols } = useControlPlane();
  const supportedProtocols = useMemo(
    () =>
      controlProtocols.length > 0
        ? controlProtocols
        : (config?.supportedProtocols ?? []),
    [controlProtocols, config?.supportedProtocols],
  );

  const { data: platforms } = useQuery({
    queryKey: ['platforms'],
    queryFn: api.listPlatforms,
    enabled: open,
  });

  const visiblePlatforms = useMemo(
    () => platforms?.filter((p) => supportedProtocols.includes(p.protocol)) ?? [],
    [platforms, supportedProtocols],
  );

  const [step, setStep] = useState(0);
  const [form, setForm] = useState<ConnectionProfile>(() =>
    emptyForm(defaultProtocol(supportedProtocols)),
  );
  const [selectedPresetKey, setSelectedPresetKey] = useState('');
  const [draftConnectionId, setDraftConnectionId] = useState<string | null>(null);
  const [validation, setValidation] = useState<ValidationState>({ status: 'idle' });
  const [testPublishWarning, setTestPublishWarning] = useState('');

  const canManage = canAction(config?.allowedActions, 'MANAGE_CONNECTIONS');
  const canPublish = canAction(config?.allowedActions, 'PUBLISH');
  const isReadOnlyDeploy = config?.deploymentMode === 'READONLY';

  useEffect(() => {
    if (!open) return;
    setStep(0);
    setForm(emptyForm(defaultProtocol(supportedProtocols)));
    setSelectedPresetKey('');
    setDraftConnectionId(null);
    setValidation({ status: 'idle' });
    setTestPublishWarning('');
    closeRef.current?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') handleCancel();
    };
    document.addEventListener('keydown', onKey);
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = prev;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- reset only when dialog opens
  }, [open, supportedProtocols.join(',')]);

  const cleanupDraft = async () => {
    if (draftConnectionId) {
      try {
        await api.deleteConnection(draftConnectionId);
      } catch {
        // best-effort cleanup
      }
      setDraftConnectionId(null);
    }
  };

  const handleCancel = () => {
    if (draftConnectionId && step < TOTAL_STEPS - 1) {
      if (!window.confirm('Discard this connection setup? The draft connection will be removed.')) {
        return;
      }
      void cleanupDraft().finally(onClose);
      return;
    }
    onClose();
  };

  const applyPreset = (preset: StreamPlatformPreset) => {
    setSelectedPresetKey(presetKey(preset));
    setForm((f) => applyPresetToForm(f, preset));
  };

  const onProtocolChange = (protocol: ProtocolType) => {
    setSelectedPresetKey('');
    setForm((f) => ({
      ...f,
      protocol,
      brokerUrl: emptyForm(protocol).brokerUrl,
    }));
  };

  const validateMutation = useMutation({
    mutationFn: async () => {
      setValidation({ status: 'pending' });
      let connectionId = draftConnectionId;
      if (!connectionId) {
        const created = await api.createConnection(form);
        if (!created.id) throw new Error('Connection create did not return an id');
        connectionId = created.id;
        setDraftConnectionId(connectionId);
      } else {
        await api.updateConnection(connectionId, { ...form, id: connectionId });
      }
      const result = await api.validateConnection(connectionId);
      return { connectionId, result };
    },
    onSuccess: (data) => {
      setDraftConnectionId(data.connectionId);
      setValidation({
        status: 'ok',
        message: data.result?.status ?? 'ok',
      });
    },
    onError: async (err) => {
      const message = err instanceof Error ? err.message : String(err);
      setValidation({ status: 'error', message });
      await cleanupDraft();
    },
  });

  const testPublishMutation = useMutation({
    mutationFn: async () => {
      if (!draftConnectionId) throw new Error('No saved connection');
      const destination =
        form.properties?.queue ??
        form.properties?.topicFilter ??
        form.properties?.entityPath ??
        form.properties?.subscription ??
        'eventore.test';
      return api.publish(draftConnectionId, {
        destination,
        payload: `eventore-wizard-probe-${Date.now()}`,
      });
    },
    onError: (err) => {
      setTestPublishWarning(err instanceof Error ? err.message : String(err));
    },
    onSuccess: () => setTestPublishWarning(''),
  });

  const finishWizard = () => {
    queryClient.invalidateQueries({ queryKey: ['connections'] });
    onClose();
  };

  const goNext = () => {
    if (step === 2 && validation.status !== 'ok') return;
    setStep((s) => Math.min(s + 1, TOTAL_STEPS - 1));
  };

  const goBack = () => setStep((s) => Math.max(s - 1, 0));

  if (!open) return null;

  if (!canManage || isReadOnlyDeploy) {
    return (
      <div className="portal-dialog-backdrop" onClick={onClose} role="presentation">
        <div className="portal-dialog" role="dialog" aria-modal="true" onClick={(e) => e.stopPropagation()}>
          <p>Connection changes are disabled in read-only deployment mode.</p>
          <button type="button" className="btn-secondary" onClick={onClose}>
            Close
          </button>
        </div>
      </div>
    );
  }

  const selectedPreset = visiblePlatforms.find((p) => presetKey(p) === selectedPresetKey);

  return (
    <div className="portal-dialog-backdrop" onClick={handleCancel} role="presentation">
      <div
        className="portal-dialog wizard-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        onClick={(e) => e.stopPropagation()}
      >
        <button
          ref={closeRef}
          type="button"
          className="portal-dialog-close"
          aria-label="Close"
          onClick={handleCancel}
        >
          ×
        </button>

        <header className="portal-dialog-header">
          <div>
            <h2 id={titleId}>New connection</h2>
            <p className="portal-dialog-tagline">
              Step {step + 1} of {TOTAL_STEPS}: {STEPS[step]}
            </p>
          </div>
        </header>

        <div className="wizard-progress" aria-hidden>
          {STEPS.map((label, i) => (
            <span key={label} className={i <= step ? 'wizard-step active' : 'wizard-step'}>
              {i + 1}. {label}
            </span>
          ))}
        </div>

        {step === 0 && (
          <section className="wizard-panel">
            <div className="form-row">
              <label>Connection name</label>
              <input
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                placeholder="e.g. local-kafka"
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
            {selectedPreset && (
              <>
                <p className="inspector-meta">{selectedPreset.description}</p>
                <p>
                  <a href={protocolGuideUrl(selectedPreset.protocol)} target="_blank" rel="noreferrer">
                    Protocol guide — {selectedPreset.protocol}
                  </a>
                </p>
              </>
            )}
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
          </section>
        )}

        {step === 1 && (
          <section className="wizard-panel">
            <p className="inspector-meta">
              Use <code>env:VAR_NAME</code> or <code>file:/path/to/secret</code> for credentials.{' '}
              <a href={SECRETS_DOC_URL} target="_blank" rel="noreferrer">
                Deployment secrets guide
              </a>
            </p>
            <div className="form-row">
              <label>Broker URL</label>
              <input
                value={form.brokerUrl}
                onChange={(e) => setForm({ ...form, brokerUrl: e.target.value })}
              />
            </div>
            <div className="form-row">
              <label>Username</label>
              <input
                value={form.credentials?.username ?? ''}
                onChange={(e) =>
                  setForm({
                    ...form,
                    credentials: { ...form.credentials, username: e.target.value },
                  })
                }
              />
            </div>
            <div className="form-row">
              <label>Password</label>
              <input
                type="password"
                value={form.credentials?.password ?? ''}
                onChange={(e) =>
                  setForm({
                    ...form,
                    credentials: { ...form.credentials, password: e.target.value },
                  })
                }
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
            <p>
              <a href={protocolGuideUrl(form.protocol)} target="_blank" rel="noreferrer">
                {form.protocol} protocol guide
              </a>
            </p>
          </section>
        )}

        {step === 2 && (
          <section className="wizard-panel">
            <p>Validate broker reachability before finishing setup.</p>
            <dl className="wizard-summary">
              <dt>Name</dt>
              <dd>{form.name}</dd>
              <dt>Protocol</dt>
              <dd>{form.protocol}</dd>
              <dt>Broker</dt>
              <dd>{form.brokerUrl}</dd>
            </dl>
            <button
              type="button"
              className="btn-primary"
              disabled={!form.name || validateMutation.isPending}
              onClick={() => validateMutation.mutate()}
            >
              {validateMutation.isPending ? 'Validating…' : 'Validate connection'}
            </button>
            {validation.status === 'ok' && (
              <p className="tag tag-ok">Validation succeeded ({validation.message})</p>
            )}
            {validation.status === 'error' && (
              <p className="stream-error">{validation.message}</p>
            )}
          </section>
        )}

        {step === 3 && (
          <section className="wizard-panel">
            <p className="tag tag-ok">Connection saved: {form.name}</p>
            <dl className="wizard-summary">
              <dt>Protocol</dt>
              <dd>{form.protocol}</dd>
              <dt>Validation</dt>
              <dd>{validation.status === 'ok' ? validation.message : '—'}</dd>
            </dl>
            {canPublish && (config?.deploymentMode === 'DEV' || config?.deploymentMode === 'ADMIN') && (
              <button
                type="button"
                className="btn-secondary"
                disabled={!draftConnectionId || testPublishMutation.isPending}
                onClick={() => testPublishMutation.mutate()}
              >
                Send test message
              </button>
            )}
            {testPublishWarning && <p className="stream-error">{testPublishWarning}</p>}
          </section>
        )}

        <div className="wizard-actions">
          {step > 0 && step < TOTAL_STEPS - 1 && (
            <button type="button" className="btn-secondary" onClick={goBack}>
              Back
            </button>
          )}
          {step < 2 && (
            <button
              type="button"
              className="btn-primary"
              disabled={step === 0 && !form.name}
              onClick={goNext}
            >
              Next
            </button>
          )}
          {step === 2 && (
            <button
              type="button"
              className="btn-primary"
              disabled={validation.status !== 'ok'}
              onClick={goNext}
            >
              Next
            </button>
          )}
          {step === 3 && (
            <>
              <button
                type="button"
                className="btn-primary"
                onClick={() => {
                  finishWizard();
                  if (draftConnectionId) {
                    navigate(`/browse?connectionId=${encodeURIComponent(draftConnectionId)}`);
                  }
                }}
              >
                Open in Browse
              </button>
              <button type="button" className="btn-secondary" onClick={finishWizard}>
                Done
              </button>
            </>
          )}
          <button type="button" className="btn-secondary" onClick={handleCancel}>
            Cancel
          </button>
        </div>
      </div>
    </div>
  );
}
