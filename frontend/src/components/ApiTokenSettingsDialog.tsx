import { useEffect, useId, useRef, useState } from 'react';
import { clearApiToken, getRuntimeConfig, getStoredApiToken, saveApiToken } from '../config/runtime';

interface Props {
  open: boolean;
  onClose: () => void;
  onSaved: () => void;
}

export default function ApiTokenSettingsDialog({ open, onClose, onSaved }: Props) {
  const titleId = useId();
  const descId = useId();
  const closeRef = useRef<HTMLButtonElement>(null);
  const [token, setToken] = useState('');

  useEffect(() => {
    if (!open) return;
    const injected = window.__EVENTORE_CONFIG__?.apiToken?.trim();
    setToken(injected ?? getStoredApiToken() ?? '');
    closeRef.current?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = prev;
    };
  }, [open, onClose]);

  if (!open) return null;

  const injectedToken = window.__EVENTORE_CONFIG__?.apiToken?.trim();

  function handleSave() {
    if (injectedToken) {
      onClose();
      return;
    }
    if (token.trim()) {
      saveApiToken(token);
    } else {
      clearApiToken();
    }
    onSaved();
    onClose();
  }

  function handleClear() {
    if (injectedToken) return;
    clearApiToken();
    setToken('');
    onSaved();
  }

  const { apiToken: activeToken } = getRuntimeConfig();

  return (
    <div className="portal-dialog-backdrop" onClick={onClose} role="presentation">
      <div
        className="portal-dialog api-token-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={descId}
        onClick={(e) => e.stopPropagation()}
      >
        <button
          ref={closeRef}
          type="button"
          className="portal-dialog-close"
          aria-label="Close"
          onClick={onClose}
        >
          ×
        </button>

        <header className="portal-dialog-header">
          <div>
            <h2 id={titleId}>API token</h2>
            <p id={descId} className="portal-dialog-tagline">
              Required when the backend has <code>eventore.security.api-token</code> configured.
            </p>
          </div>
        </header>

        {injectedToken ? (
          <p className="portal-dialog-lead">
            Token is injected by deployment config. Session overrides are disabled.
          </p>
        ) : (
          <label className="api-token-field">
            <span>Bearer token</span>
            <input
              type="password"
              value={token}
              onChange={(e) => setToken(e.target.value)}
              placeholder="Paste API token"
              autoComplete="off"
            />
          </label>
        )}

        <p className="portal-dialog-meta">
          {activeToken ? 'Token active for this browser session.' : 'No token configured.'}
        </p>

        <div className="api-token-actions">
          {!injectedToken && (
            <>
              <button type="button" className="btn-primary" onClick={handleSave}>
                Save
              </button>
              <button type="button" className="btn-secondary" onClick={handleClear}>
                Clear
              </button>
            </>
          )}
          <button type="button" className="btn-secondary" onClick={onClose}>
            Close
          </button>
        </div>
      </div>
    </div>
  );
}
